package de.rechnio.service;

import java.util.UUID;

import de.rechnio.enums.DocType;

public interface NumberingService {

    /** Naechste lueckenlose Nummer im Format PREFIX-JAHR-NNNN, z. B. RE-2026-0042. */
    String next(UUID organizationId, DocType type, int year);
}
