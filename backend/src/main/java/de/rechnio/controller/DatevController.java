package de.rechnio.controller;

import java.nio.charset.Charset;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.rechnio.config.CurrentUser;
import de.rechnio.service.DatevExportService;

@RestController
@RequestMapping("/api/exports")
public class DatevController {

    // DATEV erwartet ANSI (Windows-1252) fuer den Import.
    private static final Charset ANSI = Charset.forName("windows-1252");

    private final DatevExportService datevExportService;

    public DatevController(DatevExportService datevExportService) {
        this.datevExportService = datevExportService;
    }

    @GetMapping("/datev")
    public ResponseEntity<byte[]> datev(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = datevExportService.exportCsv(CurrentUser.from(jwt).organizationId(), from, to);
        String filename = "EXTF_Buchungsstapel_%s_%s.csv".formatted(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", ANSI))
                .body(csv.getBytes(ANSI));
    }
}
