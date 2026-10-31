package de.rechnio.config;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/** Angemeldeter Benutzer, aus dem JWT gelesen. Jede Abfrage wird auf {@link #organizationId()} beschraenkt. */
public record CurrentUser(UUID userId, UUID organizationId, String email) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("org")),
                jwt.getClaimAsString("email"));
    }
}
