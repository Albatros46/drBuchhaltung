package de.rechnio.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.rechnio.config.CurrentUser;
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
}
