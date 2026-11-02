package de.rechnio.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Artikel oder Leistung als Vorlage fuer Rechnungspositionen. */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;
    private UUID organizationId;
    private int productNumber;
    private String name;
    private String description;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private Instant createdAt;

    protected Product() {
    }

    public Product(UUID organizationId, int productNumber) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.productNumber = productNumber;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public int getProductNumber() { return productNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }
}
