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
import de.rechnio.service.DatevStammdatenService;

@RestController
@RequestMapping("/api/exports")
public class DatevController {

    // DATEV erwartet ANSI (Windows-1252) fuer den Import.
    private static final Charset ANSI = Charset.forName("windows-1252");

    private final DatevExportService datevExportService;
    private final DatevStammdatenService datevStammdatenService;

    public DatevController(DatevExportService datevExportService, DatevStammdatenService datevStammdatenService) {
        this.datevExportService = datevExportService;
        this.datevStammdatenService = datevStammdatenService;
    }

    @GetMapping("/datev")
    public ResponseEntity<byte[]> datev(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = datevExportService.exportCsv(CurrentUser.from(jwt).organizationId(), from, to);
        return csv("EXTF_Buchungsstapel_%s_%s.csv".formatted(from, to), csv);
    }

    @GetMapping("/datev/stammdaten")
    public ResponseEntity<byte[]> stammdaten(@AuthenticationPrincipal Jwt jwt) {
        String csv = datevStammdatenService.exportCsv(CurrentUser.from(jwt).organizationId());
        return csv("EXTF_Debitoren_Kreditoren.csv", csv);
    }

    private static ResponseEntity<byte[]> csv(String filename, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", ANSI))
                .body(content.getBytes(ANSI));
    }
}
