package de.rechnio.service;

import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.PdfDocument;

public interface InvoicePdfService {

    /** Rechnung, Angebot oder Storno als PDF; Entwuerfe werden sichtbar als Entwurf markiert. */
    PdfDocument render(CurrentUser user, UUID invoiceId);
}
