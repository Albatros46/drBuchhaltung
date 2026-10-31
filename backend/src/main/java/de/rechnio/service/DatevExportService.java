package de.rechnio.service;

import java.time.LocalDate;
import java.util.UUID;

public interface DatevExportService {

    /** DATEV-Buchungsstapel (EXTF 700) der Ausgangsrechnungen eines Zeitraums als CSV-Text. */
    String exportCsv(UUID organizationId, LocalDate from, LocalDate to);
}
