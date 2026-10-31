package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Erzeugt E-Rechnungen ueber die API und prueft sie mit dem Mustang-Validator (XSD, Schematron, PDF/A). */
@SpringBootTest
@AutoConfigureMockMvc
class ERechnungIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private static final String COMPLETE_ORG = """
            {"name":"Yilmaz Fliesenbau","street":"Ringstr. 5","zip":"50668","city":"Köln",
             "steuernummer":"12/345/67890","ustId":"DE123456789","kontenrahmen":"SKR03",
             "iban":"DE02120300000000202051","bic":"BYLADEM1001",
             "email":"info@yilmaz-fliesen.example","contactName":"Ali Yilmaz","phone":"+49 221 123456"%s}
            """;

    @Test
    void xrechnungIsValid() throws Exception {
        String token = setUp(COMPLETE_ORG.formatted(""));
        String invoiceId = issuedInvoice(token, customer(token, true));

        byte[] xml = call(token, get("/api/invoices/" + invoiceId + "/xrechnung"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml"))
                .andReturn().getResponse().getContentAsByteArray();

        String text = new String(xml, StandardCharsets.UTF_8);
        assertThat(text).contains("urn:xeinkauf.de:kosit:xrechnung_3.0", "<ram:TypeCode>380</ram:TypeCode>", "MTK");
        assertValid(xml, "xrechnung.xml");
    }

    @Test
    void zugferdIsValidPdfA3WithEmbeddedXml() throws Exception {
        String token = setUp(COMPLETE_ORG.formatted(""));
        String invoiceId = issuedInvoice(token, customer(token, false));

        byte[] pdf = call(token, get("/api/invoices/" + invoiceId + "/zugferd"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertValid(pdf, "zugferd.pdf");
    }

    @Test
    void kleinunternehmerUsesExemptCategory() throws Exception {
        String token = setUp(COMPLETE_ORG.formatted(",\"kleinunternehmer\":true"));
        String invoiceId = issuedInvoice(token, customer(token, true));

        byte[] xml = call(token, get("/api/invoices/" + invoiceId + "/xrechnung"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(xml, StandardCharsets.UTF_8))
                .contains("<ram:CategoryCode>E</ram:CategoryCode>", "§ 19 UStG");
        assertValid(xml, "klein.xml");
    }

    @Test
    void stornoBecomesCreditNoteReferencingTheInvoice() throws Exception {
        String token = setUp(COMPLETE_ORG.formatted(""));
        String invoiceId = issuedInvoice(token, customer(token, true));
        String stornoId = body(call(token, post("/api/invoices/" + invoiceId + "/cancel"))
                .andExpect(status().isOk())).get("id").asText();

        byte[] xml = call(token, get("/api/invoices/" + stornoId + "/xrechnung"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        String text = new String(xml, StandardCharsets.UTF_8);
        assertThat(text).contains("<ram:TypeCode>381</ram:TypeCode>").contains("RE-").doesNotContain(">-");
        assertValid(xml, "storno.xml");
    }

    @Test
    void missingXrechnungFieldsAreListed() throws Exception {
        String token = setUp("""
                {"name":"Minimal GmbH","street":"Weg 1","zip":"10115","city":"Berlin","steuernummer":"11/111/11111"}
                """);
        String invoiceId = issuedInvoice(token, customer(token, false));

        call(token, get("/api/invoices/" + invoiceId + "/xrechnung"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Angaben für die E-Rechnung fehlen"))
                .andExpect(jsonPath("$.errors.length()").value(5));
    }

    @Test
    void draftsAndOffersHaveNoERechnung() throws Exception {
        String token = setUp(COMPLETE_ORG.formatted(""));
        String draftId = body(call(token, post("/api/invoices"), draftJson(customer(token, true), "RECHNUNG")))
                .get("id").asText();
        call(token, get("/api/invoices/" + draftId + "/xrechnung")).andExpect(status().isConflict());
    }

    // --- Hilfen ---

    private static void assertValid(byte[] content, String name) {
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String report = validator.validate(content, name);
        assertThat(validator.wasCompletelyValid()).as("Validierungsbericht:%n%s", report).isTrue();
    }

    private String setUp(String orgJson) throws Exception {
        String email = UUID.randomUUID() + "@example.de";
        String token = body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"Test","email":"%s","password":"geheim1234"}
                """.formatted(email))).andExpect(status().isCreated())).get("token").asText();
        call(token, put("/api/organization"), orgJson).andExpect(status().isOk());
        return token;
    }

    private String customer(String token, boolean withEmail) throws Exception {
        String emailPart = withEmail ? ",\"email\":\"einkauf@mueller.example\"" : "";
        return body(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln"%s}
                """.formatted(emailPart))).get("id").asText();
    }

    private String issuedInvoice(String token, String customerId) throws Exception {
        String id = body(call(token, post("/api/invoices"), draftJson(customerId, "RECHNUNG"))).get("id").asText();
        call(token, post("/api/invoices/" + id + "/issue")).andExpect(status().isOk());
        return id;
    }

    private static String draftJson(String customerId, String docType) {
        return """
                {"customerId":"%s","docType":"%s","serviceDateFrom":"2026-10-01","lines":[
                  {"description":"Fliesen verlegen","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19},
                  {"description":"Fugenmaterial","quantity":3,"unit":"kg","unitPrice":4.99,"vatRate":19},
                  {"description":"Anfahrt","quantity":1,"unit":"Pausch.","unitPrice":45.00,"vatRate":19}]}
                """.formatted(customerId, docType);
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
