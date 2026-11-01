package de.rechnio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import de.rechnio.enums.DunningLevel;

/**
 * Mahnung mit berechneten Summen.
 *
 * @param totalFees Mahngebuehren aller Stufen bis einschliesslich dieser
 * @param totalDue  offener Rechnungsbetrag plus totalFees
 */
public record DunningResponse(UUID id, UUID invoiceId, DunningLevel level, String title, LocalDate issueDate,
                              LocalDate deadline, BigDecimal openAmount, BigDecimal fee, BigDecimal totalFees,
                              BigDecimal totalDue, Instant sentAt, String sentTo) {
}
