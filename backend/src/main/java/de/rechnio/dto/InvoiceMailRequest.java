package de.rechnio.dto;

import de.rechnio.enums.MailAttachment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inhalt einer Rechnungs-E-Mail. Dient auch als Vorschlag (GET .../email), den die App vorausfuellt.
 *
 * @param to mehrere Empfaenger durch Komma getrennt
 */
public record InvoiceMailRequest(
        @NotBlank String to,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank @Size(max = 10000) String message,
        @NotNull MailAttachment attachment) {
}
