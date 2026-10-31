package de.rechnio.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import de.rechnio.enums.DocType;
import de.rechnio.enums.TaxMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record InvoiceDraftRequest(@NotNull UUID customerId, DocType docType, TaxMode taxMode,
                                  LocalDate serviceDateFrom, LocalDate serviceDateTo, LocalDate dueDate,
                                  String introText, @NotNull List<@Valid InvoiceLineRequest> lines) {
}
