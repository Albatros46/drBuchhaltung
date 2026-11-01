package de.rechnio.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.rechnio.enums.EInvoiceFormat;
import de.rechnio.enums.ExpenseCategory;
import de.rechnio.enums.IncomingDocType;
import de.rechnio.enums.IncomingStatus;
import de.rechnio.enums.ValidationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Eingegangene E-Rechnung eines Lieferanten; die Daten sind aus der Originaldatei gelesen. */
@Entity
@Table(name = "incoming_invoice")
public class IncomingInvoice {

    @Id
    private UUID id;
    private UUID organizationId;
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    private EInvoiceFormat format;
    @Enumerated(EnumType.STRING)
    private IncomingDocType documentType;
    @Enumerated(EnumType.STRING)
    private IncomingStatus status = IncomingStatus.OPEN;

    private String supplierName;
    private String supplierStreet;
    private String supplierZip;
    private String supplierCity;
    private String supplierCountry;
    private String supplierVatId;
    private String supplierTaxId;
    private String supplierIban;

    private String number;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currency;
    private String buyerReference;
    private String paymentTerms;
    private BigDecimal netTotal;
    private BigDecimal vatTotal;
    private BigDecimal grossTotal;

    /** Kontierung fuer den DATEV-Export; bei CUSTOM gelten expenseAccount und expenseBuKey. */
    @Enumerated(EnumType.STRING)
    private ExpenseCategory expenseCategory;
    private Integer expenseAccount;
    private String expenseBuKey;

    @Enumerated(EnumType.STRING)
    private ValidationStatus validationStatus;
    private String validationMessages;

    private String originalFilename;
    private String sha256;
    private LocalDate paidAt;
    private Instant receivedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<IncomingInvoiceLine> lines = new ArrayList<>();

    protected IncomingInvoice() {
    }

    public IncomingInvoice(UUID organizationId, EInvoiceFormat format, String originalFilename, String sha256) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.format = format;
        this.originalFilename = originalFilename;
        this.sha256 = sha256;
        this.receivedAt = Instant.now();
    }

    public void addLine(String description, BigDecimal quantity, String unit, BigDecimal unitPrice,
                        BigDecimal vatRate, BigDecimal netAmount) {
        addLine(description, quantity, unit, unitPrice, vatRate, netAmount, null);
    }

    /** @param taxCategory Steuerkategorie nach EN 16931 (S, Z, E, AE, K, ...) oder null */
    public void addLine(String description, BigDecimal quantity, String unit, BigDecimal unitPrice,
                        BigDecimal vatRate, BigDecimal netAmount, String taxCategory) {
        lines.add(new IncomingInvoiceLine(this, lines.size() + 1, description, quantity, unit, unitPrice, vatRate,
                netAmount, taxCategory));
    }

    /** Mindestens eine Position mit Steuerschuldnerschaft des Leistungsempfaengers (§ 13b, Kategorie AE). */
    public boolean isReverseCharge() {
        return lines.stream().anyMatch(l -> "AE".equalsIgnoreCase(l.getTaxCategory()));
    }

    public void assignExpense(ExpenseCategory category, Integer account, String buKey) {
        this.expenseCategory = category;
        this.expenseAccount = category == ExpenseCategory.CUSTOM ? account : null;
        this.expenseBuKey = category == ExpenseCategory.CUSTOM ? buKey : null;
    }

    public void supplier(String name, String street, String zip, String city, String country,
                         String vatId, String taxId, String iban) {
        this.supplierName = name;
        this.supplierStreet = street;
        this.supplierZip = zip;
        this.supplierCity = city;
        this.supplierCountry = country;
        this.supplierVatId = vatId;
        this.supplierTaxId = taxId;
        this.supplierIban = iban;
    }

    public void totals(BigDecimal net, BigDecimal vat, BigDecimal gross) {
        this.netTotal = net;
        this.vatTotal = vat;
        this.grossTotal = gross;
    }

    public void validation(ValidationStatus status, String messages) {
        this.validationStatus = status;
        this.validationMessages = messages;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public EInvoiceFormat getFormat() { return format; }
    public IncomingDocType getDocumentType() { return documentType; }
    public void setDocumentType(IncomingDocType documentType) { this.documentType = documentType; }
    public IncomingStatus getStatus() { return status; }
    public void setStatus(IncomingStatus status) { this.status = status; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierStreet() { return supplierStreet; }
    public String getSupplierZip() { return supplierZip; }
    public String getSupplierCity() { return supplierCity; }
    public String getSupplierCountry() { return supplierCountry; }
    public String getSupplierVatId() { return supplierVatId; }
    public String getSupplierTaxId() { return supplierTaxId; }
    public String getSupplierIban() { return supplierIban; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getBuyerReference() { return buyerReference; }
    public void setBuyerReference(String buyerReference) { this.buyerReference = buyerReference; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public BigDecimal getNetTotal() { return netTotal; }
    public BigDecimal getVatTotal() { return vatTotal; }
    public BigDecimal getGrossTotal() { return grossTotal; }
    public ExpenseCategory getExpenseCategory() { return expenseCategory; }
    public Integer getExpenseAccount() { return expenseAccount; }
    public String getExpenseBuKey() { return expenseBuKey; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public String getValidationMessages() { return validationMessages; }
    public String getOriginalFilename() { return originalFilename; }
    public String getSha256() { return sha256; }
    public LocalDate getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDate paidAt) { this.paidAt = paidAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public List<IncomingInvoiceLine> getLines() { return lines; }
}
