package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class LogoIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void uploadShowAndDeleteLogo() throws Exception {
        String token = register();
        call(token, get("/api/organization/logo")).andExpect(status().isNoContent());

        upload(token, "logo.png", image(300, 100, "png", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(300))
                .andExpect(jsonPath("$.height").value(100))
                .andExpect(jsonPath("$.dataUri").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
        call(token, get("/api/organization/logo")).andExpect(status().isOk());

        // Ersetzen durch JPEG
        upload(token, "logo.jpg", image(200, 200, "jpeg", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataUri").value(org.hamcrest.Matchers.startsWith("data:image/jpeg;base64,")));

        call(token, delete("/api/organization/logo")).andExpect(status().isNoContent());
        call(token, get("/api/organization/logo")).andExpect(status().isNoContent());
    }

    @Test
    void largeLogosAreScaledDown() throws Exception {
        String token = register();
        upload(token, "gross.png", image(3000, 1000, "png", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(1200))
                .andExpect(jsonPath("$.height").value(400));
    }

    @Test
    void rejectsOtherFilesRegardlessOfTheirName() throws Exception {
        String token = register();
        upload(token, "logo.png", image(100, 100, "gif", false)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte ein Logo als PNG oder JPEG hochladen"));
        upload(token, "logo.png", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes()).andExpect(status().isBadRequest());
        upload(token, "logo.png", image(8, 8, "png", false)).andExpect(status().isBadRequest());

        byte[] big = new byte[1024 * 1024 + 1];
        new Random(1).nextBytes(big);
        upload(token, "logo.png", big).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Das Logo darf höchstens 1 MB groß sein"));
    }

    @Test
    void logoAppearsInPdfAndZugferdStaysValid() throws Exception {
        String token = register();
        call(token, put("/api/organization"), """
                {"name":"Yilmaz Fliesenbau","street":"Ringstr. 5","zip":"50668","city":"Köln",
                 "steuernummer":"12/345/67890","ustId":"DE123456789","kontenrahmen":"SKR03",
                 "iban":"DE02120300000000202051","email":"info@yilmaz.example"}
                """).andExpect(status().isOk());
        String customerId = json.readTree(call(token, post("/api/customers"), """
                {"name":"Müller GmbH","street":"Hauptstr. 1","zip":"50667","city":"Köln"}
                """).andReturn().getResponse().getContentAsString()).get("id").asText();
        String invoiceId = json.readTree(call(token, post("/api/invoices"), """
                {"customerId":"%s","docType":"RECHNUNG","serviceDateFrom":"2026-10-01","lines":[
                  {"description":"Fliesen verlegen","quantity":8,"unit":"m2","unitPrice":85.00,"vatRate":19}]}
                """.formatted(customerId)).andReturn().getResponse().getContentAsString()).get("id").asText();
        call(token, post("/api/invoices/" + invoiceId + "/issue")).andExpect(status().isOk());

        assertThat(imageCount(pdf(token, invoiceId, "pdf"))).as("ohne Logo").isZero();

        upload(token, "logo.png", image(400, 120, "png", true)).andExpect(status().isOk());
        assertThat(imageCount(pdf(token, invoiceId, "pdf"))).as("mit Logo").isEqualTo(1);

        byte[] zugferd = pdf(token, invoiceId, "zugferd");
        assertThat(imageCount(zugferd)).isEqualTo(1);
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String report = validator.validate(zugferd, "logo.pdf");
        assertThat(validator.wasCompletelyValid()).as("Validierungsbericht:%n%s", report).isTrue();
    }

    // --- Hilfen ---

    /** Testbild mit Flaeche und Schriftzug; auf Wunsch mit transparentem Hintergrund. */
    private static byte[] image(int w, int h, String format, boolean transparent) throws Exception {
        BufferedImage img = new BufferedImage(w, h,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        if (!transparent) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        }
        g.setColor(new Color(13, 122, 106));
        g.fillOval(w / 10, h / 10, w / 3, h * 8 / 10);
        g.setColor(new Color(22, 35, 59));
        g.drawString("YILMAZ", w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, format, out)) throw new IllegalStateException("Kein Writer fuer " + format);
        return out.toByteArray();
    }

    private static int imageCount(byte[] pdf) throws Exception {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            int count = 0;
            for (var page : doc.getPages()) {
                for (var name : page.getResources().getXObjectNames()) {
                    if (page.getResources().isImageXObject(name)) count++;
                }
            }
            return count;
        }
    }

    private byte[] pdf(String token, String invoiceId, String kind) throws Exception {
        return call(token, get("/api/invoices/" + invoiceId + "/" + kind)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions upload(String token, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "image/png", content);
        return mvc.perform(multipart("/api/organization/logo").file(file).header("Authorization", "Bearer " + token));
    }

    private String register() throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"Test","email":"%s@example.de","password":"geheim1234"}
                """.formatted(UUID.randomUUID()))).andReturn().getResponse().getContentAsString()).get("token").asText();
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
