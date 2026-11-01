package de.rechnio.enums;

/** Mahnstufen in fester Reihenfolge; die Frist gilt ab dem Datum des Schreibens. */
public enum DunningLevel {
    ZAHLUNGSERINNERUNG("Zahlungserinnerung", 10, false),
    MAHNUNG_1("1. Mahnung", 7, true),
    MAHNUNG_2("2. Mahnung", 7, true);

    private final String title;
    private final int deadlineDays;
    private final boolean withFee;

    DunningLevel(String title, int deadlineDays, boolean withFee) {
        this.title = title;
        this.deadlineDays = deadlineDays;
        this.withFee = withFee;
    }

    public String title() {
        return title;
    }

    public int deadlineDays() {
        return deadlineDays;
    }

    /** Ab der 1. Mahnung wird die Mahngebuehr der Firma berechnet; die Zahlungserinnerung ist kostenlos. */
    public boolean withFee() {
        return withFee;
    }

    /** Naechste Stufe oder null, wenn die hoechste Stufe erreicht ist. */
    public DunningLevel next() {
        DunningLevel[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }
}
