package de.rechnio.dto;

import de.rechnio.enums.ExpenseCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Kontierung einer Eingangsrechnung.
 *
 * @param account             nur bei CUSTOM: Sachkonto (4-stellig) des Kontenrahmens
 * @param buKey               nur bei CUSTOM: DATEV-BU-Schluessel, z. B. 9 (19 % Vorsteuer), 8 (7 %) oder leer
 * @param rememberForSupplier als Vorschlag fuer kuenftige Rechnungen dieses Lieferanten speichern
 */
public record ExpenseRequest(
        @NotNull ExpenseCategory category,
        @Min(1) @Max(99999) Integer account,
        @Pattern(regexp = "\\d{0,4}") String buKey,
        boolean rememberForSupplier) {
}
