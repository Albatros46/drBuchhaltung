package de.rechnio.enums;

import java.math.BigDecimal;

/**
 * Kontierungsvorschlaege fuer Eingangsrechnungen (SKR03 / SKR04). Die Konten sind gaengige
 * DATEV-Standardkonten; der Steuerberater sollte die Zuordnung pruefen. Fuer alles andere gibt es CUSTOM.
 * <p>
 * Automatikkonten (Wareneingang, §13b) rechnen die Steuer selbst; bei allen anderen setzt der Export
 * den BU-Schluessel fuer die Vorsteuer (9 = 19 %, 8 = 7 %), ausser die Kategorie ist steuerfrei.
 */
public enum ExpenseCategory {
    WARENEINGANG("Wareneingang / Material", 0, 0, Tax.AUTOMATIC),
    FREMDLEISTUNGEN("Fremdleistungen (Subunternehmer)", 3100, 5900, Tax.BU_KEY),
    MIETE("Miete / Raumkosten", 4210, 6310, Tax.BU_KEY),
    BUEROBEDARF("Bürobedarf", 4930, 6815, Tax.BU_KEY),
    PORTO("Porto", 4910, 6800, Tax.BU_KEY),
    TELEFON_INTERNET("Telefon / Internet", 4920, 6805, Tax.BU_KEY),
    SOFTWARE("Software / EDV-Wartung", 4806, 6495, Tax.BU_KEY),
    KFZ("Kfz-Kosten (Kraftstoff, Werkstatt)", 4530, 6530, Tax.BU_KEY),
    REISEKOSTEN("Reisekosten Unternehmer", 4670, 6670, Tax.BU_KEY),
    WERBUNG("Werbung", 4600, 6600, Tax.BU_KEY),
    VERSICHERUNG("Versicherungen (ohne USt)", 4360, 6400, Tax.NONE),
    FORTBILDUNG("Fortbildung", 4945, 6821, Tax.BU_KEY),
    REPARATUR("Reparatur / Instandhaltung", 4800, 6460, Tax.BU_KEY),
    BERATUNG("Rechts- und Beratungskosten", 4950, 6825, Tax.BU_KEY),
    BUCHFUEHRUNG("Buchführung / Steuerberatung", 4955, 6830, Tax.BU_KEY),
    BANKGEBUEHREN("Bankgebühren (ohne USt)", 4970, 6855, Tax.NONE),
    GWG("Geringwertige Wirtschaftsgüter (bis 800 € netto)", 480, 670, Tax.BU_KEY),
    REVERSE_CHARGE_BAU("§ 13b Bauleistung eines inländischen Unternehmers", 3120, 5920, Tax.AUTOMATIC),
    REVERSE_CHARGE_EU("§ 13b Sonstige Leistung aus dem EU-Ausland", 3123, 5923, Tax.AUTOMATIC),
    /** Eigenes Konto mit eigenem BU-Schluessel (Angaben an der Rechnung). */
    CUSTOM("Eigenes Konto", 0, 0, Tax.NONE);

    /** Wie die Vorsteuer im DATEV-Export behandelt wird. */
    public enum Tax {
        /** Automatikkonto: kein BU-Schluessel. */
        AUTOMATIC,
        /** BU-Schluessel nach Steuersatz (9 = 19 %, 8 = 7 %). */
        BU_KEY,
        /** Keine Vorsteuer. */
        NONE
    }

    private final String label;
    private final int skr03;
    private final int skr04;
    private final Tax tax;

    ExpenseCategory(String label, int skr03, int skr04, Tax tax) {
        this.label = label;
        this.skr03 = skr03;
        this.skr04 = skr04;
        this.tax = tax;
    }

    public String label() {
        return label;
    }

    public Tax tax() {
        return tax;
    }

    /** Konto fuer den Kontenrahmen; Wareneingang haengt vom Steuersatz ab. CUSTOM liefert 0. */
    public int account(Kontenrahmen kontenrahmen, BigDecimal rate) {
        boolean skr04 = kontenrahmen == Kontenrahmen.SKR04;
        if (this == WARENEINGANG) {
            int r = rate == null ? 0 : rate.intValue();
            if (r == 19) return skr04 ? 5400 : 3400;
            if (r == 7) return skr04 ? 5300 : 3300;
            return skr04 ? 5200 : 3200;
        }
        return skr04 ? this.skr04 : skr03;
    }

    /** BU-Schluessel fuer die Vorsteuer oder "" (Automatikkonto, steuerfrei, 0 %). */
    public String buKey(BigDecimal rate) {
        if (tax != Tax.BU_KEY || rate == null) return "";
        int r = rate.intValue();
        if (r == 19) return "9";
        if (r == 7) return "8";
        return "";
    }
}
