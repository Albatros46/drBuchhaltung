package de.rechnio.service;

import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.InvoiceMailRequest;
import de.rechnio.dto.InvoiceResponse;

/** Versand ausgestellter Belege per E-Mail. */
public interface InvoiceMailService {

    /** Vorschlag fuer Empfaenger, Betreff, Text und Anhang. */
    InvoiceMailRequest draft(CurrentUser user, UUID invoiceId);

    /** Sendet den Beleg und vermerkt Zeitpunkt und Empfaenger. */
    InvoiceResponse send(CurrentUser user, UUID invoiceId, InvoiceMailRequest request);
}
