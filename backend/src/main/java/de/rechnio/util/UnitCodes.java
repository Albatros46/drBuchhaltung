package de.rechnio.util;

import java.util.Locale;
import java.util.Map;

/** Uebersetzt die Einheiten der Positionen in UN/ECE-Recommendation-20-Codes fuer die E-Rechnung. */
public final class UnitCodes {

    /** "Einheit" (one), wenn keine passende Einheit bekannt ist. */
    public static final String DEFAULT = "C62";

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("stk", "H87"), Map.entry("stück", "H87"), Map.entry("st", "H87"),
            Map.entry("std", "HUR"), Map.entry("h", "HUR"), Map.entry("stunde", "HUR"), Map.entry("stunden", "HUR"),
            Map.entry("min", "MIN"),
            Map.entry("tag", "DAY"), Map.entry("tage", "DAY"),
            Map.entry("m", "MTR"), Map.entry("lfm", "MTR"),
            Map.entry("m2", "MTK"), Map.entry("m²", "MTK"), Map.entry("qm", "MTK"),
            Map.entry("m3", "MTQ"), Map.entry("m³", "MTQ"),
            Map.entry("kg", "KGM"), Map.entry("t", "TNE"),
            Map.entry("l", "LTR"),
            Map.entry("km", "KMT"),
            Map.entry("pausch.", "LS"), Map.entry("pauschal", "LS"), Map.entry("psch.", "LS"));

    private UnitCodes() {
    }

    public static String of(String unit) {
        if (unit == null) return DEFAULT;
        return CODES.getOrDefault(unit.trim().toLowerCase(Locale.GERMAN), DEFAULT);
    }
}
