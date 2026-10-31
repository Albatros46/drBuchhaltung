package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceMailIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    /** Kein echter SMTP-Server: die fertige MIME-Nachricht wird abgefangen und geprueft. */
    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void realMimeMessages() {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void draftSuggestsRecipientSubjectBodyAndZugferd() throws Exception {
        Fixture f = fixture(true);

        call(f.token, get("/api/invoices/" + f.invoiceId + "/email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value("einkauf@mueller.example"))
                .andExpect(jsonPath("$.subject").value("Rechnung " + f.number + " von Yilmaz Fliesenbau"))
                .andExpect(jsonPath("$.attachment").value("ZUGFERD"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("880,56")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("IBAN DE02120300000000202051")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ali Yilmaz")));
    }

    @Test
    void sendsZugferdAndRecordsTheDispatch() throws Exception {
        Fixture f = fixture(true);

        call(f.token, post("/api/invoices/" + f.invoiceId + "/send"), mail("einkauf@mueller.example, buchhaltung@mueller.example", "ZUGFERD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("einkauf@mueller.example, buchhaltung@mueller.example"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());

        MimeMessage sent = sentMessage();
        assertThat(sent.getAllRecipients()).extracting(a -> ((InternetAddress) a).getAddress())
                .containsExactly("einkauf@mueller.example", "buchhaltung@mueller.example");
        assertThat(((InternetAddress) sent.getFrom()[0]).getPersonal()).isEqualTo("Yilmaz Fliesenbau");
        assertThat(((InternetAddress) sent.getReplyTo()[0]).getAddress()).isEqualTo("info@yilmaz-fliesen.example");
        assertThat(sent.getSubject()).isEqualTo("Ihre Rechnung");

        List<BodyPart> attachments = attachments(sent);
        assertThat(attachments).hasSize(1);
        assertThat(attachments.getFirst().getFileName()).isEqualTo("Rechnung-" + f.number + "-ZUGFeRD.pdf");
        assertThat(attachments.getFirst().getContentType()).startsWith("application/pdf");
        byte[] pdf = attachments.getFirst().getInputStream().readAllBytes();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Versand steht im Beleg, auch nach erneutem Laden
        call(f.token, get("/api/invoices/" + f.invoiceId))
                .andExpect(jsonPath("$.sentTo").value("einkauf@mueller.example, buchhaltung@mueller.example"));
    }

    @Test
    void sendsXRechnungAsXml() throws Exception {
        Fixture f = fixture(true);

        call(f.token, post("/api/invoices/" + f.invoiceId + "/send"), mail("einkauf@mueller.example", "XRECHNUNG"))
                .andExpect(status().isOk());

        BodyPart xml = attachments(sentMessage()).getFirst();
        assertThat(xml.getFileName()).isEqualTo("XRechnung-" + f.number + ".xml");
        assertThat(xml.getContentType()).startsWith("application/xml");
    }

    @Test
    void missingERechnungDataStopsTheDispatch() throws Exception {
        Fixture f = fixture(false); // Kunde ohne E-Mail: XRechnung nicht moeglich

        call(f.token, post("/api/invoices/" + f.invoiceId + "/send"), mail("jemand@example.de", "XRECHNUNG"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("E-Mail-Adresse des Kunden")));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureIsReportedAndNothingIsRecorded() throws Exception {
        Fixture f = fixture(true);
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        call(f.token, post("/api/invoices/" + f.invoiceId + "/send"), mail("einkauf@mueller.example", "PDF"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mailserver")));
        call(f.token, get("/api/invoices/" + f.invoiceId)).andExpect(jsonPath("$.sentAt").doesNotExist());
    }

    @Test
    void rejectsInvalidAddressesAndDrafts() throws Exception {
        Fixture f = fixture(true);
        call(f.token, post("/api/invoices/" + f.invoiceId + "/send"), mail("kein-mail", "PDF"))
                .andExpect(status().isBadRequest());

        String draftId = json.readTree(call(f.token, post("/api/invoices"), draftJson(f.customerId))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        call(f.token, post("/api/invoices/" + draftId + "/send"), mail("einkauf@mueller.example", "PDF"))
                .andExpect(status().isConflict());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // --- Hilfen ---

    private record Fixture(String token, String customerId, String invoiceId, String number) {
    }

    private Fixture fixture(boolean customerEmail) throws Exception {
        String token = json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName":"Yilmaz Fliesenbau","email":"%s@example.de","password":"geheim1234"}
                        """.formatted(UUID.randomUUID()))).andReturn().getResponse().getContentAsString())
                .get("token").asText();
        call(token, put("/api/organization"), """
                {"name":"Yilmaz Fliesenbau","street":"Ringstr. 5","zip":"50668","city":"Köln",
                 "steuernummer":"12/345/67890","ustId":"DE123456789","kontenrahmen":"SKR03",
                 "iban":"DE02120300000000202051","email":"info@yilmaz-fliesen.example",
                 "contactName":"Ali Yilmaz","phone":"+49 221 123456"}
                """).andExpect(status().isOk());
        String emailPart = customerEmail ? ",\"email\":\"einkauf@mueller.example\"" : "";
        String customerId = json.readTree(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln"%s}
                """.formatted(emailPart)).andReturn().getResponse().getContentAsString()).get("id").asText();
        String invoiceId = json.readTree(call(token, post("/api/invoices"), draftJson(customerId))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        JsonNode issued = json.readTree(call(token, post("/api/invoices/" + invoiceId + "/issue"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Fixture(token, customerId, invoiceId, issued.get("number").asText());
    }

    private static String draftJson(String customerId) {
        return """
                {"customerId":"%s","docType":"RECHNUNG","serviceDateFrom":"2026-10-01","lines":[
                  {"description":"Fliesen verlegen","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19},
                  {"description":"Fugenmaterial","quantity":3,"unit":"kg","unitPrice":4.99,"vatRate":19},
                  {"description":"Anfahrt","quantity":1,"unit":"Pausch.","unitPrice":45.00,"vatRate":19}]}
                """.formatted(customerId);
    }

    private static String mail(String to, String attachment) {
        return """
                {"to":"%s","subject":"Ihre Rechnung","message":"Guten Tag,\\nanbei die Rechnung.","attachment":"%s"}
                """.formatted(to, attachment);
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static List<BodyPart> attachments(MimeMessage message) throws Exception {
        message.saveChanges();
        List<BodyPart> result = new ArrayList<>();
        collect((Multipart) message.getContent(), result);
        return result;
    }

    private static void collect(Multipart mp, List<BodyPart> out) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart part = mp.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                out.add(part);
            } else if (part.getContent() instanceof Multipart nested) {
                collect(nested, out);
            }
        }
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
}
