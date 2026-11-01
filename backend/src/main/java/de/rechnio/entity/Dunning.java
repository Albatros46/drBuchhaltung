package de.rechnio.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import de.rechnio.enums.DunningLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Zahlungserinnerung oder Mahnung zu einer offenen Rechnung. */
@Entity
@Table(name = "dunning")
public class Dunning {

    @Id
    private UUID id;
    private UUID organizationId;
    private UUID invoiceId;
    @Enumerated(EnumType.STRING)
    private DunningLevel level;
    private LocalDate issueDate;
    private LocalDate deadline;
    /** Offener Rechnungsbetrag (brutto) zum Zeitpunkt des Schreibens. */
    private BigDecimal openAmount;
    /** Gebuehr dieser Stufe. */
    private BigDecimal fee;
    private Instant sentAt;
    private String sentTo;
    private Instant createdAt;

    protected Dunning() {
    }

    public Dunning(UUID organizationId, UUID invoiceId, DunningLevel level, LocalDate issueDate,
                   BigDecimal openAmount, BigDecimal fee) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.invoiceId = invoiceId;
        this.level = level;
        this.issueDate = issueDate;
        this.deadline = issueDate.plusDays(level.deadlineDays());
        this.openAmount = openAmount;
        this.fee = fee;
        this.createdAt = Instant.now();
    }

    public void markSent(String to, Instant at) {
        this.sentTo = to;
        this.sentAt = at;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getInvoiceId() { return invoiceId; }
    public DunningLevel getLevel() { return level; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDeadline() { return deadline; }
    public BigDecimal getOpenAmount() { return openAmount; }
    public BigDecimal getFee() { return fee; }
    public Instant getSentAt() { return sentAt; }
    public String getSentTo() { return sentTo; }
}
