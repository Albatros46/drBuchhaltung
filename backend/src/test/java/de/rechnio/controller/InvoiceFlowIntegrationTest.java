package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceFlowIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private final int year = LocalDate.now(ZoneId.of("Europe/Berlin")).getYear();

    @Test
    void fullInvoiceLifecycle() throws Exception {
        String token = register("Yilmaz Fliesenbau");

        String customerId = body(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln"}
                """).andExpect(status().isCreated())).get("id").asText();

        String draft = draftJson(customerId, "2026-10-01");
        String invoiceId = body(call(token, post("/api/invoices"), draft).andExpect(status().isCreated()))
                .get("id").asText();

        // Firmenanschrift und Steuernummer fehlen noch -> §14 UStG verhindert die Ausstellung
        call(token, post("/api/invoices/" + invoiceId + "/issue"), "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(2));

        call(token, put("/api/organization"), """
                {"name":"Yilmaz Fliesenbau","street":"Ringstr. 5","zip":"50668","city":"Köln",
                 "steuernummer":"12/345/67890","kontenrahmen":"SKR03","datevBeraterNr":1001,"datevMandantNr":1}
                """).andExpect(status().isOk());

        call(token, post("/api/invoices/" + invoiceId + "/issue"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("RE-" + year + "-0001"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.grossTotal").value(862.75));

        // Festgeschriebene Rechnung ist unveraenderlich
        call(token, put("/api/invoices/" + invoiceId), draft).andExpect(status().isConflict());

        String second = body(call(token, post("/api/invoices"), draftJson(customerId, "2026-10-02"))).get("id").asText();
        call(token, post("/api/invoices/" + second + "/issue"), "")
                .andExpect(jsonPath("$.number").value("RE-" + year + "-0002"));

        call(token, post("/api/invoices/" + invoiceId + "/cancel"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("ST-" + year + "-0001"))
                .andExpect(jsonPath("$.grossTotal").value(-862.75));
        call(token, get("/api/invoices/" + invoiceId), "")
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // DATEV: zwei Rechnungen (Soll) und ein Storno (Haben) auf Erloeskonto 8400
        byte[] csvBytes = call(token, get("/api/exports/datev?from=" + year + "-01-01&to=" + year + "-12-31"), "")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(csvBytes, Charset.forName("windows-1252"));
        String[] rows = csv.split("\r\n");
        assertThat(rows[0]).startsWith("\"EXTF\";700;21;\"Buchungsstapel\";13;");
        assertThat(rows[1]).contains("Gegenkonto (ohne BU-Schlüssel)");
        assertThat(rows).hasSize(5);
        assertThat(csv).contains("862,75;\"S\";\"EUR\";;;\"\";10001;8400;");
        assertThat(csv).contains("862,75;\"H\";\"EUR\";;;\"\";10001;8400;");
        assertThat(csv).contains("\"ST-" + year + "-0001\"");
    }

    @Test
    void issuedInvoiceRendersAsPdfWithMandatoryFields() throws Exception {
        String token = register("PDF Test GmbH");
        call(token, put("/api/organization"), """
                {"name":"PDF Test GmbH","street":"Ringstr. 5","zip":"50668","city":"Köln",
                 "steuernummer":"12/345/67890","iban":"DE02120300000000202051","kontenrahmen":"SKR03"}
                """).andExpect(status().isOk());
        String customerId = body(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln"}
                """)).get("id").asText();
        String invoiceId = body(call(token, post("/api/invoices"), draftJson(customerId, "2026-10-01")))
                .get("id").asText();

        // Entwurf: PDF ist moeglich, aber deutlich als Entwurf markiert
        String draftText = pdfText(call(token, get("/api/invoices/" + invoiceId + "/pdf"), "")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(draftText).contains("ENTWURF");

        call(token, post("/api/invoices/" + invoiceId + "/issue"), "").andExpect(status().isOk());
        byte[] pdf = call(token, get("/api/invoices/" + invoiceId + "/pdf"), "")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("Rechnung-RE-" + year + "-0001.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String text = pdfText(pdf);
        assertThat(text)
                .contains("Rechnung RE-" + year + "-0001")
                .contains("Müller GmbH", "Hauptstr. 1", "50667 Köln")
                .contains("Steuernummer 12/345/67890")
                .contains("01.10.2026")                  // Leistungsdatum
                .contains("zzgl. 19 % USt auf 725,00 €")
                .contains("862,75 €")
                .doesNotContain("ENTWURF");
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replace(' ', ' ');
        }
    }

    @Test
    void organizationsCannotSeeEachOthersInvoices() throws Exception {
        String tokenA = register("Firma A");
        String tokenB = register("Firma B");
        String customerId = body(call(tokenA, post("/api/customers"), """
                {"name":"Kunde A","street":"Weg 1","zip":"10115","city":"Berlin"}
                """)).get("id").asText();
        String invoiceId = body(call(tokenA, post("/api/invoices"), draftJson(customerId, "2026-10-01")))
                .get("id").asText();

        call(tokenB, get("/api/invoices/" + invoiceId), "").andExpect(status().isNotFound());
        call(tokenB, get("/api/invoices"), "").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
    }

    private String register(String company) throws Exception {
        String email = UUID.randomUUID() + "@example.de";
        return body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"%s","email":"%s","password":"geheim1234"}
                """.formatted(company, email))).andExpect(status().isCreated())).get("token").asText();
    }

    private static String draftJson(String customerId, String serviceDate) {
        return """
                {"customerId":"%s","docType":"RECHNUNG","serviceDateFrom":"%s","lines":[
                  {"description":"Fliesen verlegen","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19},
                  {"description":"Anfahrt","quantity":1,"unit":"Pausch.","unitPrice":45.00,"vatRate":19}]}
                """.formatted(customerId, serviceDate);
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder req, String content) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (!content.isEmpty()) {
            req.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
