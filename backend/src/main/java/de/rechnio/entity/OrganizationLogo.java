package de.rechnio.entity;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Firmenlogo (PNG oder JPEG) fuer den Briefkopf. */
@Entity
@Table(name = "organization_logo")
public class OrganizationLogo {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;
    private String contentType;
    private byte[] content;
    private int width;
    private int height;
    private Instant updatedAt;

    protected OrganizationLogo() {
    }

    public OrganizationLogo(UUID organizationId, String contentType, byte[] content, int width, int height) {
        this.organizationId = organizationId;
        replace(contentType, content, width, height);
    }

    public void replace(String contentType, byte[] content, int width, int height) {
        this.contentType = contentType;
        this.content = content;
        this.width = width;
        this.height = height;
        this.updatedAt = Instant.now();
    }

    /** Fuer die PDF-Vorlage und die App: data:image/png;base64,... */
    public String dataUri() {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content);
    }

    public UUID getOrganizationId() { return organizationId; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Instant getUpdatedAt() { return updatedAt; }
}
