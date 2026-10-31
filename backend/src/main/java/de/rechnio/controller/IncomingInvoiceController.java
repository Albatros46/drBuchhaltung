package de.rechnio.controller;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.dto.IncomingInvoiceResponse;
import de.rechnio.dto.PaidRequest;
import de.rechnio.service.IncomingInvoiceService;

@RestController
@RequestMapping("/api/incoming-invoices")
public class IncomingInvoiceController {

    private final IncomingInvoiceService incomingInvoiceService;

    public IncomingInvoiceController(IncomingInvoiceService incomingInvoiceService) {
        this.incomingInvoiceService = incomingInvoiceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IncomingInvoiceResponse upload(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        return incomingInvoiceService.receive(CurrentUser.from(jwt), file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping
    public List<IncomingInvoiceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return incomingInvoiceService.list(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public IncomingInvoiceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return incomingInvoiceService.get(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/paid")
    public IncomingInvoiceResponse paid(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @RequestBody(required = false) PaidRequest request) {
        return incomingInvoiceService.markPaid(CurrentUser.from(jwt), id, request == null ? null : request.paidAt());
    }

    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        GeneratedFile file = incomingInvoiceService.original(CurrentUser.from(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
