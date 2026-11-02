package de.rechnio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Artikel anlegen, aendern, loeschen und exportieren. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void crudAndExport() throws Exception {
        String token = register();
        String id = id(call(token, post("/api/products"),
                "{\"name\":\"Fliesen verlegen\",\"unit\":\"m2\",\"unitPrice\":48.50,\"vatRate\":19}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productNumber").value(1)));
        call(token, post("/api/products"), "{\"name\":\"Anfahrt\",\"unit\":\"Pauschale\",\"unitPrice\":45,\"vatRate\":19}")
                .andExpect(jsonPath("$.productNumber").value(2));

        call(token, put("/api/products/" + id),
                "{\"name\":\"Bodenfliesen verlegen\",\"description\":\"inkl. Fugen\",\"unit\":\"m2\",\"unitPrice\":52,\"vatRate\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bodenfliesen verlegen"))
                .andExpect(jsonPath("$.unitPrice").value(52))
                .andExpect(jsonPath("$.vatRate").value(7));

        call(token, get("/api/products"), "").andExpect(jsonPath("$[0].name").value("Anfahrt"))
                .andExpect(jsonPath("$.length()").value(2));

        String csv = new String(call(token, get("/api/products/export"), "").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Artikelnummer;Bezeichnung;")
                .contains("1;\"Bodenfliesen verlegen\";\"inkl. Fugen\";\"m2\";52,00;7")
                .contains("2;\"Anfahrt\";\"\";\"Pauschale\";45,00;19");

        call(token, delete("/api/products/" + id), "").andExpect(status().isNoContent());
        call(token, get("/api/products/" + id), "").andExpect(status().isNotFound());
    }

    @Test
    void validationAndTenantIsolation() throws Exception {
        String token = register();
        call(token, post("/api/products"), "{\"name\":\"\",\"unit\":\"Stk\",\"unitPrice\":1,\"vatRate\":19}")
                .andExpect(status().isBadRequest());
        call(token, post("/api/products"), "{\"name\":\"X\",\"unit\":\"Stk\",\"unitPrice\":1,\"vatRate\":120}")
                .andExpect(status().isBadRequest());

        String id = id(call(token, post("/api/products"), "{\"name\":\"X\",\"unit\":\"Stk\",\"unitPrice\":1,\"vatRate\":19}"));
        String other = register();
        call(other, get("/api/products/" + id), "").andExpect(status().isNotFound());
        call(other, delete("/api/products/" + id), "").andExpect(status().isNotFound());
    }

    private String id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String register() throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"Test","email":"%s@example.de","password":"geheim1234"}
                """.formatted(UUID.randomUUID()))).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder req, String content) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (!content.isEmpty()) {
            req.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(req);
    }
}
