package de.rechnio.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.CustomerRequest;
import de.rechnio.dto.CustomerResponse;
import de.rechnio.service.CustomerService;
import de.rechnio.util.Csv;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return customerService.list(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return customerService.get(CurrentUser.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerRequest request) {
        return customerService.create(CurrentUser.from(jwt), request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody CustomerRequest request) {
        return customerService.update(CurrentUser.from(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        customerService.delete(CurrentUser.from(jwt), id);
    }

    /** Mit BOM, damit Excel Umlaute richtig erkennt. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt) {
        byte[] csv = (Csv.BOM + customerService.exportCsv(CurrentUser.from(jwt))).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Kunden.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
