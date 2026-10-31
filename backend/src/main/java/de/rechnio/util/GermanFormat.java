package de.rechnio.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deutsche Schreibweisen fuer Belege und E-Mails. */
public final class GermanFormat {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private GermanFormat() {
    }

    /** 1.190,00 € */
    public static String money(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(v);
    }

    /** Zahl ohne Waehrung, ohne ueberfluessige Nachkommastellen (8 / 1,5 / 19). */
    public static String plain(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.GERMANY);
        f.setMaximumFractionDigits(3);
        return f.format(v);
    }

    /** 31.10.2026 oder null */
    public static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    /** Verbindet die nicht leeren Teile. */
    public static String join(String sep, String... parts) {
        List<String> present = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) present.add(p);
        }
        return String.join(sep, present);
    }
}
