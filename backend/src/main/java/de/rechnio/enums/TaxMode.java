package de.rechnio.enums;

public enum TaxMode {
    /** Regelbesteuerung mit 19 % oder 7 %. */
    STANDARD(null),
    /** §19 UStG: keine Umsatzsteuer ausweisen. */
    KLEINUNTERNEHMER("Gemäß § 19 UStG wird keine Umsatzsteuer berechnet."),
    /** §13b UStG: Steuerschuldnerschaft des Leistungsempfaengers. */
    REVERSE_CHARGE("Steuerschuldnerschaft des Leistungsempfängers (§ 13b UStG).");

    private final String legalNote;

    TaxMode(String legalNote) {
        this.legalNote = legalNote;
    }

    /** Pflichthinweis, der auf der Rechnung erscheinen muss (oder null). */
    public String legalNote() {
        return legalNote;
    }
}
