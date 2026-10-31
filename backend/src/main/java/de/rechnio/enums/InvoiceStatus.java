package de.rechnio.enums;

public enum InvoiceStatus {
    /** Entwurf: frei bearbeitbar, noch ohne Nummer. */
    DRAFT,
    /** Ausgestellt: Nummer vergeben, festgeschrieben (GoBD). */
    ISSUED,
    PAID,
    /** Durch eine Stornorechnung aufgehoben. */
    CANCELLED
}
