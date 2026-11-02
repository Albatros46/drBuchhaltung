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

/** Kunden bearbeiten, loeschen und als CSV exportieren. */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void editDeleteAndExport() throws Exception {
        String token = register();
        String id = create(token, "{\"name\":\"Bäckerei Süß\",\"city\":\"Köln\"}");
        call(token, put("/api/customers/" + id), "{\"name\":\"Bäckerei Süß GmbH\",\"city\":\"Bonn\",\"email\":\"=cmd\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bäckerei Süß GmbH"))
                .andExpect(jsonPath("$.city").value("Bonn"));

        byte[] bytes = call(token, get("/api/customers/export"), "").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Kundennummer;Name;");
        assertThat(csv).contains("1;\"Bäckerei Süß GmbH\";\"\";\"\";\"Bonn\";\"DE\";\"'=cmd\"");

        call(token, delete("/api/customers/" + id), "").andExpect(status().isNoContent());
        call(token, get("/api/customers/" + id), "").andExpect(status().isNotFound());
    }

    @Test
    void customerWithInvoicesCannotBeDeleted() throws Exception {
        String token = register();
        String id = create(token, "{\"name\":\"Mit Beleg\"}");
        call(token, post("/api/invoices"), """
                {"customerId":"%s","docType":"RECHNUNG","lines":[
                  {"description":"Arbeit","quantity":1,"unit":"Std","unitPrice":50,"vatRate":19}]}
                """.formatted(id)).andExpect(status().isCreated());

        call(token, delete("/api/customers/" + id), "").andExpect(status().isConflict());
        call(token, get("/api/customers/" + id), "").andExpect(status().isOk());
    }

    @Test
    void otherOrganizationsCannotDelete() throws Exception {
        String id = create(register(), "{\"name\":\"Fremd\"}");
        call(register(), delete("/api/customers/" + id), "").andExpect(status().isNotFound());
    }

    private String create(String token, String body) throws Exception {
        return json.readTree(call(token, post("/api/customers"), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
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
