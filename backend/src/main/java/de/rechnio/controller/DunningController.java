package de.rechnio.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.DunningMailRequest;
import de.rechnio.dto.DunningResponse;
import de.rechnio.dto.PdfDocument;
import de.rechnio.service.DunningService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class DunningController {

    private final DunningService dunningService;

    public DunningController(DunningService dunningService) {
        this.dunningService = dunningService;
    }

    @GetMapping("/invoices/{invoiceId}/dunnings")
    public List<DunningResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invoiceId) {
        return dunningService.list(CurrentUser.from(jwt), invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/dunnings")
    @ResponseStatus(HttpStatus.CREATED)
    public DunningResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invoiceId) {
        return dunningService.create(CurrentUser.from(jwt), invoiceId);
    }

    @GetMapping("/dunnings/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        PdfDocument pdf = dunningService.pdf(CurrentUser.from(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    @GetMapping("/dunnings/{id}/email")
    public DunningMailRequest emailDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return dunningService.draft(CurrentUser.from(jwt), id);
    }

    @PostMapping("/dunnings/{id}/send")
    public DunningResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody DunningMailRequest request) {
        return dunningService.send(CurrentUser.from(jwt), id, request);
    }
}
