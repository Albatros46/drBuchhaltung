package de.rechnio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import de.rechnio.entity.Invoice;
import de.rechnio.entity.InvoiceLine;
import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.enums.TaxMode;

public record InvoiceResponse(UUID id, UUID customerId, DocType docType, InvoiceStatus status, TaxMode taxMode,
                              String number, LocalDate issueDate, LocalDate serviceDateFrom,
                              LocalDate serviceDateTo, LocalDate dueDate, String introText,
                              BigDecimal netTotal, BigDecimal vatTotal, BigDecimal grossTotal,
                              UUID cancelsInvoiceId, LocalDate paidAt, boolean locked, String legalNote,
                              List<Line> lines) {

    public record Line(int position, String description, BigDecimal quantity, String unit,
                       BigDecimal unitPrice, BigDecimal vatRate, BigDecimal netAmount) {

        static Line from(InvoiceLine l) {
            return new Line(l.getPosition(), l.getDescription(), l.getQuantity(), l.getUnit(),
                    l.getUnitPrice(), l.getVatRate(), l.getNetAmount());
        }
    }

    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getCustomerId(), i.getDocType(), i.getStatus(), i.getTaxMode(),
                i.getNumber(), i.getIssueDate(), i.getServiceDateFrom(), i.getServiceDateTo(), i.getDueDate(),
                i.getIntroText(), i.getNetTotal(), i.getVatTotal(), i.getGrossTotal(), i.getCancelsInvoiceId(),
                i.getPaidAt(), i.isLocked(), i.getTaxMode().legalNote(),
                i.getLines().stream().map(Line::from).toList());
    }
}
