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
import de.rechnio.dto.InvoiceDraftRequest;
import de.rechnio.dto.InvoiceResponse;
import de.rechnio.dto.PaidRequest;
import de.rechnio.dto.PdfDocument;
import de.rechnio.service.InvoicePdfService;
import de.rechnio.service.InvoiceService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping
    public List<InvoiceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return invoiceService.list(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return invoiceService.get(CurrentUser.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InvoiceDraftRequest request) {
        return invoiceService.createDraft(CurrentUser.from(jwt), request);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody InvoiceDraftRequest request) {
        return invoiceService.updateDraft(CurrentUser.from(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        invoiceService.deleteDraft(CurrentUser.from(jwt), id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        PdfDocument pdf = invoicePdfService.render(CurrentUser.from(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return invoiceService.issue(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/paid")
    public InvoiceResponse paid(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @RequestBody(required = false) PaidRequest request) {
        return invoiceService.markPaid(CurrentUser.from(jwt), id, request == null ? null : request.paidAt());
    }

    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return invoiceService.cancel(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse convert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return invoiceService.convertToInvoice(CurrentUser.from(jwt), id);
    }
}
