package de.rechnio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import de.rechnio.entity.IncomingInvoice;
import de.rechnio.entity.IncomingInvoiceLine;
import de.rechnio.enums.EInvoiceFormat;
import de.rechnio.enums.ExpenseCategory;
import de.rechnio.enums.Kontenrahmen;
import de.rechnio.util.Kontierung;
import de.rechnio.enums.IncomingDocType;
import de.rechnio.enums.IncomingStatus;
import de.rechnio.enums.ValidationStatus;

public record IncomingInvoiceResponse(UUID id, EInvoiceFormat format, IncomingDocType documentType,
                                      IncomingStatus status, String supplierName, String supplierStreet,
                                      String supplierZip, String supplierCity, String supplierCountry,
                                      String supplierVatId, String supplierTaxId, String supplierIban,
                                      String number, LocalDate issueDate, LocalDate dueDate, String currency,
                                      String buyerReference, String paymentTerms, BigDecimal netTotal,
                                      BigDecimal vatTotal, BigDecimal grossTotal, ValidationStatus validationStatus,
                                      List<String> validationMessages, String originalFilename, LocalDate paidAt,
                                      Instant receivedAt, Integer kreditorAccount, Expense expense,
                                      List<Line> lines) {

    /**
     * Kontierung fuer DATEV.
     *
     * @param chosen   false, solange nur der automatische Vorschlag gilt
     * @param accounts Gegenkonto(en) im Kontenrahmen der Firma
     */
    public record Expense(ExpenseCategory category, String label, String accounts, String buKey, boolean chosen,
                          boolean reverseCharge) {
    }

    public record Line(int position, String description, BigDecimal quantity, String unit, BigDecimal unitPrice,
                       BigDecimal vatRate, BigDecimal netAmount) {

        static Line from(IncomingInvoiceLine l) {
            return new Line(l.getPosition(), l.getDescription(), l.getQuantity(), l.getUnit(), l.getUnitPrice(),
                    l.getVatRate(), l.getNetAmount());
        }
    }

    /** @param kreditorAccount DATEV-Kreditorenkonto des Lieferanten (70000 + Lieferantennummer) oder null */
    public static IncomingInvoiceResponse from(IncomingInvoice i, Integer kreditorAccount, Kontenrahmen kontenrahmen) {
        ExpenseCategory c = Kontierung.effective(i);
        Expense expense = new Expense(c, c.label(), Kontierung.accountLabel(c, kontenrahmen, i.getExpenseAccount()),
                c == ExpenseCategory.CUSTOM ? i.getExpenseBuKey() : null, i.getExpenseCategory() != null,
                i.isReverseCharge());
        List<String> messages = i.getValidationMessages() == null || i.getValidationMessages().isBlank()
                ? List.of() : Arrays.asList(i.getValidationMessages().split("\n"));
        return new IncomingInvoiceResponse(i.getId(), i.getFormat(), i.getDocumentType(), i.getStatus(),
                i.getSupplierName(), i.getSupplierStreet(), i.getSupplierZip(), i.getSupplierCity(),
                i.getSupplierCountry(), i.getSupplierVatId(), i.getSupplierTaxId(), i.getSupplierIban(),
                i.getNumber(), i.getIssueDate(), i.getDueDate(), i.getCurrency(), i.getBuyerReference(),
                i.getPaymentTerms(), i.getNetTotal(), i.getVatTotal(), i.getGrossTotal(), i.getValidationStatus(),
                messages, i.getOriginalFilename(), i.getPaidAt(), i.getReceivedAt(), kreditorAccount, expense,
                i.getLines().stream().map(Line::from).toList());
    }
}
