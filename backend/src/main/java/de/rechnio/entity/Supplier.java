package de.rechnio.entity;

import java.time.Instant;
import java.util.UUID;

import de.rechnio.enums.ExpenseCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Lieferant; im DATEV-Export als Kreditorenkonto 70000 + Lieferantennummer verwendet. */
@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    private UUID id;
    private UUID organizationId;
    private int supplierNumber;
    private String matchKey;
    private String name;
    private String vatId;
    private String taxId;
    /** Zuletzt gewaehlte Kontierung; Vorschlag fuer die naechsten Rechnungen dieses Lieferanten. */
    @Enumerated(EnumType.STRING)
    private ExpenseCategory expenseCategory;
    private Integer expenseAccount;
    private String expenseBuKey;
    private Instant createdAt;

    protected Supplier() {
    }

    public Supplier(UUID organizationId, int supplierNumber, String matchKey, String name, String vatId, String taxId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.supplierNumber = supplierNumber;
        this.matchKey = matchKey;
        this.name = name;
        this.vatId = vatId;
        this.taxId = taxId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public int getSupplierNumber() { return supplierNumber; }
    public String getMatchKey() { return matchKey; }
    public String getName() { return name; }
    public String getVatId() { return vatId; }
    public String getTaxId() { return taxId; }
    public ExpenseCategory getExpenseCategory() { return expenseCategory; }
    public Integer getExpenseAccount() { return expenseAccount; }
    public String getExpenseBuKey() { return expenseBuKey; }

    public void rememberExpense(ExpenseCategory category, Integer account, String buKey) {
        this.expenseCategory = category;
        this.expenseAccount = account;
        this.expenseBuKey = buKey;
    }
}
