package de.rechnio.util;

import java.math.BigDecimal;

import de.rechnio.enums.Kontenrahmen;
import de.rechnio.enums.TaxMode;

/**
 * DATEV-Kontenzuordnung. Erloes- und Aufwandskonten sind Automatikkonten: DATEV rechnet die
 * Umsatz- bzw. Vorsteuer aus dem Bruttobetrag heraus. Der Steuerberater kann einzelne Buchungen umkontieren.
 */
public final class DatevAccounts {

    /** Debitoren (Kunden): 10000 + Kundennummer. */
    public static final int DEBITOR_BASE = 10000;
    /** Kreditoren (Lieferanten): 70000 + Lieferantennummer. */
    public static final int KREDITOR_BASE = 70000;

    private DatevAccounts() {
    }

    /** Erloeskonto fuer Ausgangsrechnungen. */
    public static int revenue(Kontenrahmen kontenrahmen, TaxMode mode, BigDecimal rate) {
        boolean skr04 = kontenrahmen == Kontenrahmen.SKR04;
        return switch (mode) {
            case KLEINUNTERNEHMER -> skr04 ? 4185 : 8195;
            case REVERSE_CHARGE -> skr04 ? 4337 : 8337;
            case STANDARD -> {
                int r = rate.intValue();
                if (r == 19) yield skr04 ? 4400 : 8400;
                if (r == 7) yield skr04 ? 4300 : 8300;
                yield skr04 ? 4100 : 8100;
            }
        };
    }

    /** Aufwandskonto fuer Eingangsrechnungen: Wareneingang mit 19 %, 7 % oder ohne Vorsteuer. */
    public static int expense(Kontenrahmen kontenrahmen, BigDecimal rate) {
        boolean skr04 = kontenrahmen == Kontenrahmen.SKR04;
        int r = rate == null ? 0 : rate.intValue();
        if (r == 19) return skr04 ? 5400 : 3400;
        if (r == 7) return skr04 ? 5300 : 3300;
        return skr04 ? 5200 : 3200;
    }
}
