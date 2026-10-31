package de.rechnio.service;

import de.rechnio.dto.OutgoingMail;

/** SMTP-Versand fuer Belege. */
public interface MailService {

    /**
     * Prueft die Empfaengerliste und liefert sie normalisiert ("a@x.de, b@y.de").
     * Ungueltige Adressen fuehren zu HTTP 400.
     */
    String normalizeRecipients(String to);

    /**
     * Sendet die E-Mail und liefert die normalisierten Empfaenger.
     * Ist der Mailserver nicht erreichbar, folgt HTTP 502.
     */
    String send(OutgoingMail mail);
}
