package de.rechnio.dto;

import de.rechnio.enums.ExpenseCategory;

/**
 * Auswahlmoeglichkeit fuer die Kontierung im Kontenrahmen der Firma.
 *
 * @param accounts Kontonummer(n) zur Anzeige, z. B. "4930" oder "3400 / 3300 / 3200"
 * @param taxNote  wie die Vorsteuer gebucht wird
 */
public record ExpenseCategoryOption(ExpenseCategory code, String label, String accounts, String taxNote) {
}
