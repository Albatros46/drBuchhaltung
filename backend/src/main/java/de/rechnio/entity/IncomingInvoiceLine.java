package de.rechnio.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "incoming_invoice_line")
public class IncomingInvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "incoming_invoice_id")
    private IncomingInvoice invoice;

    private int position;
    private String description;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private BigDecimal netAmount;
    private String taxCategory;

    protected IncomingInvoiceLine() {
    }

    IncomingInvoiceLine(IncomingInvoice invoice, int position, String description, BigDecimal quantity, String unit,
                        BigDecimal unitPrice, BigDecimal vatRate, BigDecimal netAmount, String taxCategory) {
        this.taxCategory = taxCategory;
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.position = position;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.netAmount = netAmount;
    }

    public int getPosition() { return position; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public String getTaxCategory() { return taxCategory; }
}
