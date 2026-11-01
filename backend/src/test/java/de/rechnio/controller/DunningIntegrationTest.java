package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Mahnwesen mit kontrollierter Uhr: Faelligkeit und Fristen werden durch Vorspulen des Datums geprueft. */
@SpringBootTest
@AutoConfigureMockMvc
class DunningIntegrationTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @MockitoBean
    Clock clock;
    @MockitoBean
    JavaMailSender mailSender;

    private final AtomicReference<LocalDate> today = new AtomicReference<>();

    @BeforeEach
    void controllableClock() {
        when(clock.getZone()).thenReturn(BERLIN);
        when(clock.instant()).thenAnswer(i -> today.get().atTime(10, 0).atZone(BERLIN).toInstant());
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    // 8 x 85,00 + 3 x 4,99 + 45,00 = 739,97 netto; brutto 880,56. Ausgestellt 01.03., faellig 15.03.

    @Test
    void notYetOverdueCannotBeDunned() throws Exception {
        Fixture f = fixture();
        today.set(LocalDate.of(2026, 3, 15));
        call(f.token, post("/api/invoices/" + f.invoiceId + "/dunnings"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("fällig am 15.03.2026")));
    }

    @Test
    void escalatesThroughAllLevelsRespectingDeadlines() throws Exception {
        Fixture f = fixture();
        String path = "/api/invoices/" + f.invoiceId + "/dunnings";

        today.set(LocalDate.of(2026, 3, 20));
        call(f.token, post(path)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("ZAHLUNGSERINNERUNG"))
                .andExpect(jsonPath("$.deadline").value("2026-03-30"))
                .andExpect(jsonPath("$.fee").value(0.0))
                .andExpect(jsonPath("$.totalDue").value(880.56));

        call(f.token, post(path)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("läuft noch bis 30.03.2026")));

        today.set(LocalDate.of(2026, 3, 31));
        call(f.token, post(path)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("MAHNUNG_1"))
                .andExpect(jsonPath("$.deadline").value("2026-04-07"))
                .andExpect(jsonPath("$.fee").value(5.0))
                .andExpect(jsonPath("$.totalDue").value(885.56));

        today.set(LocalDate.of(2026, 4, 8));
        call(f.token, post(path)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("MAHNUNG_2"))
                .andExpect(jsonPath("$.totalFees").value(10.0))
                .andExpect(jsonPath("$.totalDue").value(890.56));

        today.set(LocalDate.of(2026, 4, 30));
        call(f.token, post(path)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("höchste Mahnstufe")));

        call(f.token, get(path)).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Zahlungserinnerung"))
                .andExpect(jsonPath("$[2].title").value("2. Mahnung"));
    }

    @Test
    void pdfListsInvoiceFeesAndTotal() throws Exception {
        Fixture f = fixture();
        String path = "/api/invoices/" + f.invoiceId + "/dunnings";
        today.set(LocalDate.of(2026, 3, 20));
        call(f.token, post(path));
        today.set(LocalDate.of(2026, 3, 31));
        String id = body(call(f.token, post(path))).get("id").asText();

        byte[] pdf = call(f.token, get("/api/dunnings/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("1-Mahnung-" + f.number + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        String text;
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replace(' ', ' ');
        }
        assertThat(text)
                .contains("1. Mahnung zu Rechnung " + f.number)
                .contains("Müller GmbH", "Hauptstr. 1")
                .contains("880,56 €")
                .contains("Mahngebühr 1. Mahnung vom 31.03.2026")
                .contains("885,56 €")
                .contains("bis zum 07.04.2026")
                .doesNotContain("Mahngebühr Zahlungserinnerung");
    }

    @Test
    void sendsTheDunningLetterByMail() throws Exception {
        Fixture f = fixture();
        today.set(LocalDate.of(2026, 3, 20));
        String id = body(call(f.token, post("/api/invoices/" + f.invoiceId + "/dunnings"))).get("id").asText();

        JsonNode draft = body(call(f.token, get("/api/dunnings/" + id + "/email")).andExpect(status().isOk()));
        assertThat(draft.get("to").asText()).isEqualTo("einkauf@mueller.example");
        assertThat(draft.get("subject").asText()).isEqualTo("Zahlungserinnerung zu Rechnung " + f.number + " – Yilmaz Fliesenbau");
        // Betraege enthalten ein geschuetztes Leerzeichen vor dem Euro-Zeichen
        assertThat(draft.get("message").asText().replace(' ', ' '))
                .contains("880,56 €", "30.03.2026", "IBAN DE02120300000000202051");

        call(f.token, post("/api/dunnings/" + id + "/send"), json.writeValueAsString(draft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("einkauf@mueller.example"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        Multipart mp = (Multipart) sent.getContent();
        boolean hasPdf = false;
        for (int i = 0; i < mp.getCount(); i++) {
            Part p = mp.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
                assertThat(p.getFileName()).isEqualTo("Zahlungserinnerung-" + f.number + ".pdf");
                hasPdf = true;
            }
        }
        assertThat(hasPdf).isTrue();
    }

    @Test
    void paidInvoicesAndOtherCompaniesAreRejected() throws Exception {
        Fixture f = fixture();
        today.set(LocalDate.of(2026, 3, 20));
        String id = body(call(f.token, post("/api/invoices/" + f.invoiceId + "/dunnings"))).get("id").asText();

        String other = register();
        call(other, get("/api/dunnings/" + id + "/pdf")).andExpect(status().isNotFound());
        call(other, post("/api/invoices/" + f.invoiceId + "/dunnings")).andExpect(status().isNotFound());

        call(f.token, post("/api/invoices/" + f.invoiceId + "/paid")).andExpect(status().isOk());
        today.set(LocalDate.of(2026, 4, 30));
        call(f.token, post("/api/invoices/" + f.invoiceId + "/dunnings"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("offene Rechnungen")));
    }

    // --- Hilfen ---

    private record Fixture(String token, String invoiceId, String number) {
    }

    /** Firma mit 5,00 € Mahngebuehr; Rechnung am 01.03.2026 ausgestellt, faellig 15.03.2026. */
    private Fixture fixture() throws Exception {
        today.set(LocalDate.of(2026, 3, 1));
        String token = register();
        call(token, put("/api/organization"), """
                {"name":"Yilmaz Fliesenbau","street":"Ringstr. 5","zip":"50668","city":"Köln",
                 "steuernummer":"12/345/67890","kontenrahmen":"SKR03","iban":"DE02120300000000202051",
                 "email":"info@yilmaz-fliesen.example","contactName":"Ali Yilmaz","dunningFee":5.00}
                """).andExpect(status().isOk());
        String customerId = body(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln","email":"einkauf@mueller.example"}
                """)).get("id").asText();
        String invoiceId = body(call(token, post("/api/invoices"), """
                {"customerId":"%s","docType":"RECHNUNG","serviceDateFrom":"2026-02-20","lines":[
                  {"description":"Fliesen verlegen","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19},
                  {"description":"Fugenmaterial","quantity":3,"unit":"kg","unitPrice":4.99,"vatRate":19},
                  {"description":"Anfahrt","quantity":1,"unit":"Pausch.","unitPrice":45.00,"vatRate":19}]}
                """.formatted(customerId))).get("id").asText();
        JsonNode issued = body(call(token, post("/api/invoices/" + invoiceId + "/issue")).andExpect(status().isOk()));
        assertThat(issued.get("dueDate").asText()).isEqualTo("2026-03-15");
        return new Fixture(token, invoiceId, issued.get("number").asText());
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
