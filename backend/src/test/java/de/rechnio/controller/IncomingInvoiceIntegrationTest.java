package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mustangproject.CII.CIIToUBL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Empfang von E-Rechnungen: Firma A (Lieferant) erzeugt Rechnungen ueber die API,
 * Firma B (Kunde) liest sie ein. So werden echte, validierbare Dateien verwendet.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IncomingInvoiceIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    // 8 x 85,00 + 3 x 4,99 + 45,00 = 739,97 netto; 19 % = 140,59; brutto 880,56
    private static final String GROSS = "880.56";

    @Test
    void receivesXRechnungCii() throws Exception {
        Supplier s = supplier();
        String buyer = register();

        JsonNode in = upload(buyer, "xr.xml", s.xrechnung()).andExpect(status().isCreated()).andReturnJson();
        assertThat(in.get("format").asText()).isEqualTo("XRECHNUNG_CII");
        assertThat(in.get("documentType").asText()).isEqualTo("RECHNUNG");
        assertThat(in.get("status").asText()).isEqualTo("OPEN");
        assertThat(in.get("supplierName").asText()).isEqualTo("Yilmaz Fliesenbau");
        assertThat(in.get("supplierVatId").asText()).isEqualTo("DE123456789");
        assertThat(in.get("supplierIban").asText()).isEqualTo("DE02120300000000202051");
        assertThat(in.get("number").asText()).startsWith("RE-");
        assertThat(in.get("issueDate").asText()).isNotBlank();
        assertThat(in.get("netTotal").decimalValue()).isEqualByComparingTo("739.97");
        assertThat(in.get("vatTotal").decimalValue()).isEqualByComparingTo("140.59");
        assertThat(in.get("grossTotal").decimalValue()).isEqualByComparingTo(GROSS);
        assertThat(in.get("lines")).hasSize(3);
        assertThat(in.get("lines").get(0).get("description").asText()).isEqualTo("Fliesen verlegen");
        assertThat(in.get("lines").get(0).get("unit").asText()).isEqualTo("m²");
        assertThat(in.get("lines").get(0).get("unitCode").asText()).isEqualTo("MTK");
        assertThat(in.get("lines").get(1).get("unit").asText()).isEqualTo("kg");
        assertThat(in.get("lines").get(2).get("unit").asText()).as("Pausch. -> LS -> Pauschale").isEqualTo("Pauschale");
        assertThat(in.get("validationStatus").asText()).isEqualTo("VALID");
    }

    @Test
    void receivesZugferdPdfAndKeepsTheOriginal() throws Exception {
        Supplier s = supplier();
        String buyer = register();
        byte[] pdf = s.zugferd();

        JsonNode in = upload(buyer, "rechnung.pdf", pdf).andExpect(status().isCreated()).andReturnJson();
        assertThat(in.get("format").asText()).isEqualTo("ZUGFERD");
        assertThat(in.get("grossTotal").decimalValue()).isEqualByComparingTo(GROSS);
        assertThat(in.get("validationStatus").asText()).isEqualTo("VALID");

        byte[] original = call(buyer, get("/api/incoming-invoices/" + in.get("id").asText() + "/original"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(original).isEqualTo(pdf);
    }

    @Test
    void receivesXRechnungUbl() throws Exception {
        Supplier s = supplier();
        String buyer = register();
        Path dir = Files.createTempDirectory("ubl");
        Path cii = dir.resolve("cii.xml");
        Path ubl = dir.resolve("ubl.xml");
        Files.write(cii, s.xrechnung());
        new CIIToUBL().convert(cii.toFile(), ubl.toFile());

        JsonNode in = upload(buyer, "ubl.xml", Files.readAllBytes(ubl)).andExpect(status().isCreated()).andReturnJson();
        assertThat(in.get("format").asText()).isEqualTo("XRECHNUNG_UBL");
        assertThat(in.get("supplierName").asText()).isEqualTo("Yilmaz Fliesenbau");
        assertThat(in.get("grossTotal").decimalValue()).isEqualByComparingTo(GROSS);
        assertThat(in.get("lines")).hasSize(3);
    }

    @Test
    void readsStornoAsCreditNote() throws Exception {
        Supplier s = supplier();
        String stornoId = s.body(s.call(post("/api/invoices/" + s.invoiceId() + "/cancel"))).get("id").asText();
        byte[] xml = s.call(get("/api/invoices/" + stornoId + "/xrechnung"))
                .andReturn().getResponse().getContentAsByteArray();

        JsonNode in = upload(register(), "storno.xml", xml).andExpect(status().isCreated()).andReturnJson();
        assertThat(in.get("documentType").asText()).isEqualTo("GUTSCHRIFT");
        assertThat(in.get("grossTotal").decimalValue()).isEqualByComparingTo(GROSS);
    }

    @Test
    void invalidButReadableFileIsStoredWithFindings() throws Exception {
        Supplier s = supplier();
        String xml = new String(s.xrechnung(), StandardCharsets.UTF_8);
        String tampered = xml.replace("<ram:GrandTotalAmount>" + GROSS, "<ram:GrandTotalAmount>999.99");
        assertThat(tampered).isNotEqualTo(xml);

        JsonNode in = upload(register(), "falsch.xml", tampered.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated()).andReturnJson();
        assertThat(in.get("validationStatus").asText()).isEqualTo("INVALID");
        assertThat(in.get("validationMessages")).isNotEmpty();
    }

    @Test
    void rejectsDuplicatesPlainPdfsAndOtherXml() throws Exception {
        Supplier s = supplier();
        String buyer = register();
        byte[] xml = s.xrechnung();
        upload(buyer, "a.xml", xml).andExpect(status().isCreated());
        upload(buyer, "b.xml", xml).andExpect(status().isConflict());

        byte[] plainPdf = s.call(get("/api/invoices/" + s.invoiceId() + "/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        upload(buyer, "normal.pdf", plainPdf).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("keine E-Rechnung")));

        upload(buyer, "x.xml", "<note><to>Tove</to></note>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void externalEntitiesAreNotResolved() throws Exception {
        Supplier s = supplier();
        Path secret = Files.createTempFile("secret", ".txt");
        Files.writeString(secret, "GEHEIM-12345");
        String xml = new String(s.xrechnung(), StandardCharsets.UTF_8)
                .replaceFirst("<\\?xml[^>]*\\?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<!DOCTYPE x [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>")
                .replaceFirst("<ram:Name>Yilmaz Fliesenbau</ram:Name>", "<ram:Name>&xxe;</ram:Name>");
        assertThat(xml).contains("&xxe;");

        String response = upload(register(), "xxe.xml", xml.getBytes(StandardCharsets.UTF_8))
                .actions.andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("GEHEIM-12345");
    }

    @Test
    void datevExportBooksIncomingInvoicesOnKreditors() throws Exception {
        String buyer = register();
        call(buyer, put("/api/organization"), """
                {"name":"Käufer GmbH","street":"Weg 1","zip":"10115","city":"Berlin","steuernummer":"11/111/11111",
                 "kontenrahmen":"SKR03","datevBeraterNr":1001,"datevMandantNr":7}
                """).andExpect(status().isOk());

        Supplier yilmaz = supplier();
        JsonNode first = upload(buyer, "a.xml", yilmaz.xrechnung()).andReturnJson();
        assertThat(first.get("kreditorAccount").asInt()).isEqualTo(70001);
        Supplier yilmazAgain = withInvoice(yilmaz.token());
        assertThat(upload(buyer, "b.xml", yilmazAgain.xrechnung()).andReturnJson().get("kreditorAccount").asInt())
                .as("gleicher Lieferant, gleicher Kreditor").isEqualTo(70001);
        Supplier other = supplier("Holz Meier KG", "DE987654321");
        assertThat(upload(buyer, "c.xml", other.xrechnung()).andReturnJson().get("kreditorAccount").asInt())
                .isEqualTo(70002);
        String stornoId = yilmaz.body(yilmaz.call(post("/api/invoices/" + yilmaz.invoiceId() + "/cancel")))
                .get("id").asText();
        byte[] storno = yilmaz.call(get("/api/invoices/" + stornoId + "/xrechnung"))
                .andReturn().getResponse().getContentAsByteArray();
        upload(buyer, "d.xml", storno).andExpect(status().isCreated());

        int year = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin")).getYear();
        byte[] bytes = call(buyer, get("/api/exports/datev?from=" + year + "-01-01&to=" + year + "-12-31"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        String[] rows = csv.split("\r\n");

        assertThat(rows).hasSize(2 + 4);
        assertThat(rows[0]).contains("\"Rechnungsausgang und -eingang ");
        // Rechnung: Kreditor im Haben gegen Wareneingang 19 % (SKR03 3400)
        assertThat(csv).containsSubsequence("880,56;\"H\";\"EUR\";;;\"\";70001;3400;", "880,56;\"H\";\"EUR\";;;\"\";70001;3400;");
        assertThat(csv).contains("880,56;\"H\";\"EUR\";;;\"\";70002;3400;");
        // Gutschrift des Lieferanten: Kreditor im Soll
        assertThat(csv).contains("880,56;\"S\";\"EUR\";;;\"\";70001;3400;");
        assertThat(csv).contains("\"Yilmaz Fliesenbau\"", "\"Holz Meier KG\"");
    }

    @Test
    void datevStammdatenListsDebitorsAndKreditors() throws Exception {
        String buyer = register();
        call(buyer, put("/api/organization"), """
                {"name":"Käufer GmbH","street":"Weg 1","zip":"10115","city":"Berlin","steuernummer":"11/111/11111",
                 "kontenrahmen":"SKR04","datevBeraterNr":1001,"datevMandantNr":7}
                """).andExpect(status().isOk());
        call(buyer, post("/api/customers"), """
                {"name":"Café Süß \\"Am Markt\\"","street":"Marktplatz 3","zip":"50667","city":"Köln",
                 "email":"info@cafe.example","ustId":"DE 111 222 333"}
                """).andExpect(status().isCreated());
        upload(buyer, "a.xml", supplier().xrechnung()).andExpect(status().isCreated());

        byte[] bytes = call(buyer, get("/api/exports/datev/stammdaten"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"EXTF_Debitoren_Kreditoren.csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        String[] rows = new String(bytes, java.nio.charset.Charset.forName("windows-1252")).split("\r\n");

        assertThat(rows).hasSize(2 + 2);
        assertThat(rows[0]).startsWith("\"EXTF\";700;16;\"Debitoren/Kreditoren\";5;").contains(";1001;7;").contains("\"04\"");
        assertThat(rows[1].split(";", -1)).hasSize(254).startsWith("Konto", "Name (Adressatentyp Unternehmen)");

        String[] debitor = rows[2].split(";", -1);
        assertThat(debitor).hasSize(254);
        assertThat(debitor[0]).isEqualTo("10001");
        assertThat(debitor[5]).isEqualTo("\"Café Süß \"\"Am Markt\"\"\"");
        assertThat(debitor[6]).isEqualTo("0");
        assertThat(debitor[7]).as("Kurzbezeichnung max. 15 Zeichen").isEqualTo("\"Café Süß \"\"Am Ma\"");
        assertThat(debitor[8]).isEqualTo("\"DE\"");
        assertThat(debitor[9]).isEqualTo("\"111222333\"");
        assertThat(debitor[15]).isEqualTo("\"Marktplatz 3\"");
        assertThat(debitor[17]).isEqualTo("\"50667\"");
        assertThat(debitor[32]).isEqualTo("\"info@cafe.example\"");
        assertThat(debitor[98]).isEqualTo("\"1\"");

        String[] kreditor = rows[3].split(";", -1);
        assertThat(kreditor).hasSize(254);
        assertThat(kreditor[0]).isEqualTo("70001");
        assertThat(kreditor[5]).isEqualTo("\"Yilmaz Fliesenbau\"");
        assertThat(kreditor[15]).as("Anschrift aus der Eingangsrechnung").isNotEqualTo("\"\"");
        assertThat(kreditor[44]).as("IBAN aus der Eingangsrechnung").startsWith("\"DE");
        assertThat(kreditor[48]).isEqualTo("1");
    }

    @Test
    void markPaidAndTenantIsolation() throws Exception {
        Supplier s = supplier();
        String buyer = register();
        String id = upload(buyer, "xr.xml", s.xrechnung()).andReturnJson().get("id").asText();

        call(buyer, post("/api/incoming-invoices/" + id + "/paid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        call(buyer, get("/api/incoming-invoices")).andExpect(jsonPath("$.length()").value(1));

        String other = register();
        call(other, get("/api/incoming-invoices/" + id)).andExpect(status().isNotFound());
        call(other, get("/api/incoming-invoices/" + id + "/original")).andExpect(status().isNotFound());
    }

    // --- Hilfen ---

    /** Lieferant mit vollstaendigen Stammdaten und einer ausgestellten Rechnung. */
    private record Supplier(IncomingInvoiceIntegrationTest t, String token, String invoiceId) {
        byte[] xrechnung() throws Exception {
            return call(get("/api/invoices/" + invoiceId + "/xrechnung")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
        }

        byte[] zugferd() throws Exception {
            return call(get("/api/invoices/" + invoiceId + "/zugferd")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
        }

        ResultActions call(MockHttpServletRequestBuilder req) throws Exception {
            return t.call(token, req);
        }

        JsonNode body(ResultActions r) throws Exception {
            return t.json.readTree(r.andReturn().getResponse().getContentAsString());
        }
    }

    private Supplier supplier() throws Exception {
        return supplier("Yilmaz Fliesenbau", "DE123456789");
    }

    private Supplier supplier(String name, String ustId) throws Exception {
        String token = register();
        call(token, put("/api/organization"), """
                {"name":"%s","street":"Ringstr. 5","zip":"50668","city":"Köln",
                 "steuernummer":"12/345/67890","ustId":"%s","kontenrahmen":"SKR03",
                 "iban":"DE02120300000000202051","bic":"BYLADEM1001",
                 "email":"info@yilmaz-fliesen.example","contactName":"Ali Yilmaz","phone":"+49 221 123456"}
                """.formatted(name, ustId)).andExpect(status().isOk());
        return withInvoice(token);
    }

    /** Stellt beim selben Lieferanten eine weitere Rechnung aus. */
    private Supplier withInvoice(String token) throws Exception {
        String customerId = json.readTree(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln","email":"einkauf@mueller.example"}
                """).andReturn().getResponse().getContentAsString()).get("id").asText();
        String invoiceId = json.readTree(call(token, post("/api/invoices"), """
                {"customerId":"%s","docType":"RECHNUNG","serviceDateFrom":"2026-10-01","lines":[
                  {"description":"Fliesen verlegen","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19},
                  {"description":"Fugenmaterial","quantity":3,"unit":"kg","unitPrice":4.99,"vatRate":19},
                  {"description":"Anfahrt","quantity":1,"unit":"Pausch.","unitPrice":45.00,"vatRate":19}]}
                """.formatted(customerId)).andReturn().getResponse().getContentAsString()).get("id").asText();
        call(token, post("/api/invoices/" + invoiceId + "/issue")).andExpect(status().isOk());
        return new Supplier(this, token, invoiceId);
    }

    private String register() throws Exception {
        String email = UUID.randomUUID() + "@example.de";
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"Test","email":"%s","password":"geheim1234"}
                """.formatted(email))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    /** ResultActions mit bequemem JSON-Zugriff. */
    private final class Upload {
        private final ResultActions actions;

        Upload(ResultActions actions) {
            this.actions = actions;
        }

        Upload andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            actions.andExpect(m);
            return this;
        }

        JsonNode andReturnJson() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }

    private Upload upload(String token, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", content);
        return new Upload(mvc.perform(multipart("/api/incoming-invoices").file(file)
                .header("Authorization", "Bearer " + token)));
    }

    ResultActions call(String token, MockHttpServletRequestBuilder req) throws Exception {
        return call(token, req, "");
    }

    ResultActions call(String token, MockHttpServletRequestBuilder req, String content) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (!content.isEmpty()) {
            req.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(req);
    }
}
