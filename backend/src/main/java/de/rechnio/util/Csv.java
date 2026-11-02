package de.rechnio.util;

import java.math.BigDecimal;

/** CSV fuer Excel (deutsch): Semikolon, Text in Anfuehrungszeichen, Dezimalkomma. */
public final class Csv {

    /** Byte Order Mark, damit Excel UTF-8 erkennt. */
    public static final String BOM = "﻿";

    private Csv() {
    }

    /** Immer in Anfuehrungszeichen; fuehrende = + - @ werden entschaerft, damit Excel keine Formel ausfuehrt. */
    public static String cell(String v) {
        if (v == null) return "\"\"";
        String s = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static String number(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static String money(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
