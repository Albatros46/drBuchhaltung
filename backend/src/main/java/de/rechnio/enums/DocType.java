package de.rechnio.enums;

public enum DocType {
    ANGEBOT("AN"),
    RECHNUNG("RE"),
    STORNO("ST");

    private final String prefix;

    DocType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
