package de.rechnio.service;

import java.util.UUID;

/** DATEV-Stammdaten der Personenkonten (Debitoren und Kreditoren). */
public interface DatevStammdatenService {

    /** Debitoren (Kunden) und Kreditoren (Lieferanten) im DATEV-Format Debitoren/Kreditoren als CSV-Text. */
    String exportCsv(UUID organizationId);
}
