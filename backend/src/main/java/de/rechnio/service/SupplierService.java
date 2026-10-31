package de.rechnio.service;

import de.rechnio.entity.IncomingInvoice;
import de.rechnio.entity.Supplier;

public interface SupplierService {

    /**
     * Ordnet die Eingangsrechnung einem Lieferanten zu und legt ihn bei Bedarf mit der naechsten
     * Lieferantennummer an. Erkannt wird ueber USt-IdNr., sonst Steuernummer, sonst Name.
     */
    Supplier assign(IncomingInvoice invoice);
}
