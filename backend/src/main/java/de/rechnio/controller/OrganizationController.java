package de.rechnio.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.LogoResponse;
import de.rechnio.dto.OrganizationDto;
import de.rechnio.service.OrganizationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public OrganizationDto get(@AuthenticationPrincipal Jwt jwt) {
        return organizationService.get(CurrentUser.from(jwt));
    }

    @PutMapping
    public OrganizationDto update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrganizationDto dto) {
        return organizationService.update(CurrentUser.from(jwt), dto);
    }

    /** 204, wenn noch kein Logo hinterlegt ist. */
    @GetMapping("/logo")
    public ResponseEntity<LogoResponse> logo(@AuthenticationPrincipal Jwt jwt) {
        return organizationService.logo(CurrentUser.from(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LogoResponse uploadLogo(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
            throws IOException {
        return organizationService.uploadLogo(CurrentUser.from(jwt), file.getBytes());
    }

    @DeleteMapping("/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLogo(@AuthenticationPrincipal Jwt jwt) {
        organizationService.deleteLogo(CurrentUser.from(jwt));
    }
}
