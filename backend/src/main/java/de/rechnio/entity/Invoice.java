package de.rechnio.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.enums.TaxMode;
import de.rechnio.util.VatCalculator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    private UUID id;
    private UUID organizationId;
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    private DocType docType;
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;
    @Enumerated(EnumType.STRING)
    private TaxMode taxMode;

    private String number;
    private LocalDate issueDate;
    private LocalDate serviceDateFrom;
    private LocalDate serviceDateTo;
    private LocalDate dueDate;
    private String introText;
    private BigDecimal netTotal = BigDecimal.ZERO;
    private BigDecimal vatTotal = BigDecimal.ZERO;
    private BigDecimal grossTotal = BigDecimal.ZERO;
    private UUID cancelsInvoiceId;
    private LocalDate paidAt;
    private Instant lockedAt;
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(UUID organizationId, UUID customerId, DocType docType, TaxMode taxMode) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.customerId = customerId;
        this.docType = docType;
        this.taxMode = taxMode;
        this.status = InvoiceStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public void clearLines() {
        lines.clear();
        recalculate();
    }

    /** Fuegt eine Position an. Bei §19 und §13b wird kein Steuersatz ausgewiesen. */
    public void addLine(String description, BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal vatRate) {
        BigDecimal rate = taxMode == TaxMode.STANDARD ? vatRate : BigDecimal.ZERO;
        lines.add(new InvoiceLine(this, lines.size() + 1, description, quantity, unit, unitPrice, rate));
        recalculate();
    }

    private void recalculate() {
        VatCalculator.Totals t = VatCalculator.totals(lines);
        this.netTotal = t.net();
        this.vatTotal = t.vat();
        this.grossTotal = t.gross();
    }

    /** Vergibt Nummer und Datum und schreibt den Beleg fest (GoBD). */
    public void lock(String number, LocalDate issueDate, Instant now) {
        this.number = number;
        this.issueDate = issueDate;
        this.lockedAt = now;
        this.status = InvoiceStatus.ISSUED;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public DocType getDocType() { return docType; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public TaxMode getTaxMode() { return taxMode; }
    public void setTaxMode(TaxMode taxMode) { this.taxMode = taxMode; }
    public String getNumber() { return number; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getServiceDateFrom() { return serviceDateFrom; }
    public void setServiceDateFrom(LocalDate d) { this.serviceDateFrom = d; }
    public LocalDate getServiceDateTo() { return serviceDateTo; }
    public void setServiceDateTo(LocalDate d) { this.serviceDateTo = d; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getIntroText() { return introText; }
    public void setIntroText(String introText) { this.introText = introText; }
    public BigDecimal getNetTotal() { return netTotal; }
    public BigDecimal getVatTotal() { return vatTotal; }
    public BigDecimal getGrossTotal() { return grossTotal; }
    public UUID getCancelsInvoiceId() { return cancelsInvoiceId; }
    public void setCancelsInvoiceId(UUID id) { this.cancelsInvoiceId = id; }
    public LocalDate getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDate paidAt) { this.paidAt = paidAt; }
    public Instant getLockedAt() { return lockedAt; }
    public List<InvoiceLine> getLines() { return lines; }
}
