package de.rechnio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** E-Mail zu einer Mahnung; das Mahnschreiben wird immer als PDF angehaengt. */
public record DunningMailRequest(
        @NotBlank String to,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank @Size(max = 10000) String message) {
}
