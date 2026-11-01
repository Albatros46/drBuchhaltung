package de.rechnio.service;

import java.util.List;
import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.DunningMailRequest;
import de.rechnio.dto.DunningResponse;
import de.rechnio.dto.PdfDocument;

/** Mahnwesen: Zahlungserinnerung, 1. und 2. Mahnung fuer ueberfaellige Rechnungen. */
public interface DunningService {

    List<DunningResponse> list(CurrentUser user, UUID invoiceId);

    /**
     * Legt die naechste Mahnstufe an. Voraussetzungen: Rechnung offen und ueberfaellig,
     * Frist der vorherigen Stufe abgelaufen, hoechste Stufe noch nicht erreicht.
     */
    DunningResponse create(CurrentUser user, UUID invoiceId);

    PdfDocument pdf(CurrentUser user, UUID dunningId);

    DunningMailRequest draft(CurrentUser user, UUID dunningId);

    DunningResponse send(CurrentUser user, UUID dunningId, DunningMailRequest request);
}
