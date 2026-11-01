package de.rechnio.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.ExpenseCategoryOption;
import de.rechnio.dto.ExpenseRequest;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.dto.IncomingInvoiceResponse;

/** Empfang von E-Rechnungen: einlesen, pruefen, Original aufbewahren. */
public interface IncomingInvoiceService {

    /** Liest ZUGFeRD-PDF, XRechnung (CII oder UBL) oder EN-16931-XML ein und prueft die Datei. */
    IncomingInvoiceResponse receive(CurrentUser user, String filename, byte[] content);

    List<IncomingInvoiceResponse> list(CurrentUser user);

    IncomingInvoiceResponse get(CurrentUser user, UUID id);

    IncomingInvoiceResponse markPaid(CurrentUser user, UUID id, LocalDate paidAt);

    /** Die unveraenderte Originaldatei. */
    GeneratedFile original(CurrentUser user, UUID id);

    /** Kontierung (Gegenkonto) fuer den DATEV-Export setzen, optional als Vorschlag fuer den Lieferanten merken. */
    IncomingInvoiceResponse assignExpense(CurrentUser user, UUID id, ExpenseRequest request);

    /** Waehlbare Kontierungen mit den Konten im Kontenrahmen der Firma. */
    List<ExpenseCategoryOption> expenseCategories(CurrentUser user);
}
