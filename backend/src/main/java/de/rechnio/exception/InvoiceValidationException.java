package de.rechnio.exception;

import java.util.List;

/** Fehlende Pflichtangaben nach §14 UStG: die Rechnung darf nicht ausgestellt werden. */
public class InvoiceValidationException extends RuntimeException {

    private final List<String> errors;

    public InvoiceValidationException(List<String> errors) {
        this("Pflichtangaben fehlen (§14 UStG)", errors);
    }

    public InvoiceValidationException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
