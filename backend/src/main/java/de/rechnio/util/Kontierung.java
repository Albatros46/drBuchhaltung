package de.rechnio.util;

import java.math.BigDecimal;

import de.rechnio.entity.IncomingInvoice;
import de.rechnio.enums.ExpenseCategory;
import de.rechnio.enums.Kontenrahmen;

/** Gegenkonto und BU-Schluessel einer Eingangsrechnung fuer den DATEV-Export. */
public final class Kontierung {

    private Kontierung() {
    }

    /** Gewaehlte Kategorie oder der Vorschlag, falls (noch) keine gewaehlt ist. */
    public static ExpenseCategory effective(IncomingInvoice inv) {
        return inv.getExpenseCategory() != null ? inv.getExpenseCategory() : suggestion(inv);
    }

    /**
     * § 13b (Positionen mit Steuerkategorie AE): Lieferant im Inland -> Bauleistung, sonst EU-Leistung.
     * Ohne § 13b: Wareneingang.
     */
    public static ExpenseCategory suggestion(IncomingInvoice inv) {
        if (!inv.isReverseCharge()) return ExpenseCategory.WARENEINGANG;
        String country = inv.getSupplierCountry();
        return country == null || country.isBlank() || "DE".equalsIgnoreCase(country)
                ? ExpenseCategory.REVERSE_CHARGE_BAU : ExpenseCategory.REVERSE_CHARGE_EU;
    }

    public static int account(IncomingInvoice inv, Kontenrahmen kontenrahmen, BigDecimal rate) {
        ExpenseCategory c = effective(inv);
        if (c == ExpenseCategory.CUSTOM) return inv.getExpenseAccount() == null ? 0 : inv.getExpenseAccount();
        return c.account(kontenrahmen, rate);
    }

    public static String buKey(IncomingInvoice inv, BigDecimal rate) {
        ExpenseCategory c = effective(inv);
        if (c == ExpenseCategory.CUSTOM) return inv.getExpenseBuKey() == null ? "" : inv.getExpenseBuKey();
        return c.buKey(rate);
    }

    /** Anzeige der Konten, z. B. "3400 / 3300 / 3200" bei Wareneingang oder "4930". */
    public static String accountLabel(ExpenseCategory c, Kontenrahmen kontenrahmen, Integer customAccount) {
        if (c == ExpenseCategory.CUSTOM) return customAccount == null ? "" : String.valueOf(customAccount);
        if (c == ExpenseCategory.WARENEINGANG) {
            return c.account(kontenrahmen, new BigDecimal("19")) + " / " + c.account(kontenrahmen, new BigDecimal("7"))
                    + " / " + c.account(kontenrahmen, BigDecimal.ZERO);
        }
        return String.format("%04d", c.account(kontenrahmen, null));
    }
}
