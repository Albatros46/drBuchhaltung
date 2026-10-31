package de.rechnio.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;
    private UUID organizationId;
    private String email;
    private String passwordHash;
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(UUID organizationId, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
}
