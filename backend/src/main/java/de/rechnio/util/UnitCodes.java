package de.rechnio.util;

import java.util.Locale;
import java.util.Map;

/** Uebersetzt die Einheiten der Positionen in UN/ECE-Recommendation-20-Codes fuer die E-Rechnung und zurueck. */
public final class UnitCodes {

    /** "Einheit" (one), wenn keine passende Einheit bekannt ist. */
    public static final String DEFAULT = "C62";

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("stk", "H87"), Map.entry("stück", "H87"), Map.entry("st", "H87"),
            Map.entry("std", "HUR"), Map.entry("h", "HUR"), Map.entry("stunde", "HUR"), Map.entry("stunden", "HUR"),
            Map.entry("min", "MIN"),
            Map.entry("tag", "DAY"), Map.entry("tage", "DAY"),
            Map.entry("woche", "WEE"), Map.entry("wochen", "WEE"),
            Map.entry("monat", "MON"), Map.entry("monate", "MON"),
            Map.entry("m", "MTR"), Map.entry("lfm", "MTR"),
            Map.entry("m2", "MTK"), Map.entry("m²", "MTK"), Map.entry("qm", "MTK"),
            Map.entry("m3", "MTQ"), Map.entry("m³", "MTQ"),
            Map.entry("kg", "KGM"), Map.entry("t", "TNE"),
            Map.entry("l", "LTR"),
            Map.entry("km", "KMT"),
            Map.entry("pauschale", "LS"), Map.entry("pausch.", "LS"), Map.entry("pauschal", "LS"),
            Map.entry("psch.", "LS"));

    /** Anzeige der Codes aus eingehenden E-Rechnungen. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("H87", "Stk"), Map.entry("C62", "Einh."), Map.entry("EA", "Stk"), Map.entry("XPP", "Stk"),
            Map.entry("HUR", "Std"), Map.entry("MIN", "Min"), Map.entry("DAY", "Tag"), Map.entry("WEE", "Woche"),
            Map.entry("MON", "Monat"), Map.entry("ANN", "Jahr"),
            Map.entry("MTR", "m"), Map.entry("MTK", "m²"), Map.entry("MTQ", "m³"), Map.entry("KMT", "km"),
            Map.entry("KGM", "kg"), Map.entry("TNE", "t"), Map.entry("LTR", "l"),
            Map.entry("KWH", "kWh"), Map.entry("LS", "Pauschale"), Map.entry("SET", "Set"), Map.entry("PR", "Paar"));

    private UnitCodes() {
    }

    public static String of(String unit) {
        if (unit == null) return DEFAULT;
        return CODES.getOrDefault(unit.trim().toLowerCase(Locale.GERMAN), DEFAULT);
    }

    /** Lesbare Einheit zu einem UN/ECE-Code; unbekannte Codes bleiben unveraendert. */
    public static String label(String code) {
        if (code == null) return null;
        return LABELS.getOrDefault(code.trim().toUpperCase(Locale.ROOT), code);
    }
}
