package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Kontierung von Eingangsrechnungen und ihre Wirkung auf den DATEV-Export. */
@SpringBootTest
@AutoConfigureMockMvc
class KontierungIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private final int year = LocalDate.now(ZoneId.of("Europe/Berlin")).getYear();

    @Test
    void defaultIsWareneingangAndChoiceChangesTheExport() throws Exception {
        String buyer = buyer("SKR03");
        JsonNode in = receive(buyer, supplierXml("Papier Schmidt", "DE111111111", false));
        assertThat(in.at("/expense/category").asText()).isEqualTo("WARENEINGANG");
        assertThat(in.at("/expense/chosen").asBoolean()).isFalse();
        assertThat(datev(buyer)).contains(";70001;3400;\"\";");

        call(buyer, put("/api/incoming-invoices/" + in.get("id").asText() + "/expense"),
                "{\"category\":\"BUEROBEDARF\",\"rememberForSupplier\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expense.category").value("BUEROBEDARF"))
                .andExpect(jsonPath("$.expense.accounts").value("4930"))
                .andExpect(jsonPath("$.expense.chosen").value(true));
        // 19 % Vorsteuer per BU-Schluessel 9
        assertThat(datev(buyer)).contains(";70001;4930;\"9\";").doesNotContain(";3400;");
    }

    @Test
    void rememberedChoiceIsUsedForTheNextInvoiceOfTheSupplier() throws Exception {
        String buyer = buyer("SKR03");
        Supplier s = supplier("Telekom Demo", "DE222222222", false);
        JsonNode first = receive(buyer, s.xml());
        call(buyer, put("/api/incoming-invoices/" + first.get("id").asText() + "/expense"),
                "{\"category\":\"TELEFON_INTERNET\",\"rememberForSupplier\":true}").andExpect(status().isOk());

        JsonNode second = receive(buyer, s.nextXml());
        assertThat(second.at("/expense/category").asText()).isEqualTo("TELEFON_INTERNET");
        assertThat(second.at("/expense/chosen").asBoolean()).isTrue();
    }

    @Test
    void customAccountNeedsANumberAndIsExportedWithItsBuKey() throws Exception {
        String buyer = buyer("SKR03");
        String id = receive(buyer, supplierXml("Sonstiges GmbH", "DE333333333", false)).get("id").asText();

        call(buyer, put("/api/incoming-invoices/" + id + "/expense"), "{\"category\":\"CUSTOM\"}")
                .andExpect(status().isBadRequest());
        call(buyer, put("/api/incoming-invoices/" + id + "/expense"), "{\"category\":\"CUSTOM\",\"buKey\":\"abc\",\"account\":4980}")
                .andExpect(status().isBadRequest());
        call(buyer, put("/api/incoming-invoices/" + id + "/expense"), "{\"category\":\"CUSTOM\",\"account\":4980,\"buKey\":\"9\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expense.accounts").value("4980"))
                .andExpect(jsonPath("$.expense.buKey").value("9"));
        assertThat(datev(buyer)).contains(";70001;4980;\"9\";");
    }

    @Test
    void skr04UsesItsOwnAccounts() throws Exception {
        String buyer = buyer("SKR04");
        String id = receive(buyer, supplierXml("Papier Schmidt", "DE111111111", false)).get("id").asText();
        assertThat(datev(buyer)).contains(";70001;5400;\"\";");
        call(buyer, put("/api/incoming-invoices/" + id + "/expense"), "{\"category\":\"BUEROBEDARF\"}")
                .andExpect(jsonPath("$.expense.accounts").value("6815"));
        assertThat(datev(buyer)).contains(";70001;6815;\"9\";");

        call(buyer, get("/api/incoming-invoices/expense-categories"))
                .andExpect(jsonPath("$[0].code").value("WARENEINGANG"))
                .andExpect(jsonPath("$[0].accounts").value("5400 / 5300 / 5200"));
    }

    @Test
    void reverseChargeInvoiceIsRecognisedAndBookedOnTheAutomaticAccount() throws Exception {
        String buyer = buyer("SKR03");
        JsonNode in = receive(buyer, supplierXml("Bau Subunternehmer", "DE444444444", true));
        assertThat(in.at("/expense/reverseCharge").asBoolean()).isTrue();
        assertThat(in.at("/expense/category").asText()).isEqualTo("REVERSE_CHARGE_BAU");
        assertThat(in.get("vatTotal").decimalValue()).isEqualByComparingTo("0");
        // Nettobetrag auf das § 13b-Automatikkonto, ohne BU-Schluessel
        assertThat(datev(buyer)).contains("680,00;\"H\";\"EUR\";;;\"\";70001;3120;\"\";");
    }

    // --- Hilfen ---

    private record Supplier(KontierungIntegrationTest t, String token, String customerId, boolean reverseCharge) {
        byte[] xml() throws Exception {
            return t.issueAndExport(token, customerId, reverseCharge);
        }

        byte[] nextXml() throws Exception {
            return t.issueAndExport(token, customerId, reverseCharge);
        }
    }

    private byte[] supplierXml(String name, String ustId, boolean reverseCharge) throws Exception {
        return supplier(name, ustId, reverseCharge).xml();
    }

    private Supplier supplier(String name, String ustId, boolean reverseCharge) throws Exception {
        String token = register();
        call(token, put("/api/organization"), """
                {"name":"%s","street":"Weg 1","zip":"50667","city":"Köln","steuernummer":"12/345/67890",
                 "ustId":"%s","kontenrahmen":"SKR03","iban":"DE02120300000000202051",
                 "email":"info@lieferant.example","contactName":"Max Muster","phone":"+49 221 1"}
                """.formatted(name, ustId)).andExpect(status().isOk());
        String customerId = body(call(token, post("/api/customers"), """
                {"name":"Käufer GmbH","street":"Hauptstr. 1","zip":"10115","city":"Berlin",
                 "email":"einkauf@kaeufer.example","ustId":"DE999999999"}
                """)).get("id").asText();
        return new Supplier(this, token, customerId, reverseCharge);
    }

    byte[] issueAndExport(String token, String customerId, boolean reverseCharge) throws Exception {
        String id = body(call(token, post("/api/invoices"), """
                {"customerId":"%s","docType":"RECHNUNG","taxMode":"%s","serviceDateFrom":"2026-10-01","lines":[
                  {"description":"Leistung","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19}]}
                """.formatted(customerId, reverseCharge ? "REVERSE_CHARGE" : "STANDARD"))).get("id").asText();
        call(token, post("/api/invoices/" + id + "/issue")).andExpect(status().isOk());
        return call(token, get("/api/invoices/" + id + "/xrechnung")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String buyer(String kontenrahmen) throws Exception {
        String token = register();
        call(token, put("/api/organization"), """
                {"name":"Käufer GmbH","street":"Hauptstr. 1","zip":"10115","city":"Berlin",
                 "steuernummer":"11/111/11111","kontenrahmen":"%s"}
                """.formatted(kontenrahmen)).andExpect(status().isOk());
        return token;
    }

    private JsonNode receive(String token, byte[] xml) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "r.xml", "application/xml", xml);
        return body(mvc.perform(multipart("/api/incoming-invoices").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()));
    }

    private String datev(String token) throws Exception {
        byte[] bytes = call(token, get("/api/exports/datev?from=" + year + "-01-01&to=" + year + "-12-31"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new String(bytes, Charset.forName("windows-1252"));
    }

    private String register() throws Exception {
        return body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"Test","email":"%s@example.de","password":"geheim1234"}
                """.formatted(UUID.randomUUID())))).get("token").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder req) throws Exception {
        return call(token, req, "");
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
