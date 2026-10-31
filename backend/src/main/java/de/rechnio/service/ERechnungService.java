package de.rechnio.service;

import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.GeneratedFile;

/** Elektronische Rechnungen nach EN 16931 fuer ausgestellte Rechnungen und Stornos. */
public interface ERechnungService {

    /** XRechnung 3.0 (CII-Syntax) als XML, z. B. fuer oeffentliche Auftraggeber mit Leitweg-ID. */
    GeneratedFile xrechnung(CurrentUser user, UUID invoiceId);

    /** ZUGFeRD / Factur-X (Profil EN16931): PDF/A-3 mit eingebettetem XML. */
    GeneratedFile zugferd(CurrentUser user, UUID invoiceId);
}
