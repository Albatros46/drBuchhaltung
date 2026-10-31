package de.rechnio.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** GoBD: unveraenderliches Protokoll aller relevanten Aenderungen. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;
    private UUID organizationId;
    private String entityType;
    private UUID entityId;
    private String action;
    private String detail;
    private String actor;
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID organizationId, String entityType, UUID entityId, String action, String detail, String actor) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.detail = detail;
        this.actor = actor;
        this.createdAt = Instant.now();
    }

    public UUID getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
}
