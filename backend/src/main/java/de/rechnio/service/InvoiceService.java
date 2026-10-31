package de.rechnio.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.InvoiceDraftRequest;
import de.rechnio.dto.InvoiceResponse;

public interface InvoiceService {

    List<InvoiceResponse> list(CurrentUser user);

    InvoiceResponse get(CurrentUser user, UUID id);

    InvoiceResponse createDraft(CurrentUser user, InvoiceDraftRequest request);

    InvoiceResponse updateDraft(CurrentUser user, UUID id, InvoiceDraftRequest request);

    void deleteDraft(CurrentUser user, UUID id);

    /** Vergibt die Nummer und schreibt den Beleg fest. Danach sind keine Aenderungen mehr moeglich (GoBD). */
    InvoiceResponse issue(CurrentUser user, UUID id);

    InvoiceResponse markPaid(CurrentUser user, UUID id, LocalDate paidAt);

    /** Erstellt eine Stornorechnung mit negativen Betraegen; die Originalrechnung bleibt erhalten. */
    InvoiceResponse cancel(CurrentUser user, UUID id);

    /** Uebernimmt ein Angebot als neuen Rechnungsentwurf. */
    InvoiceResponse convertToInvoice(CurrentUser user, UUID offerId);
}
