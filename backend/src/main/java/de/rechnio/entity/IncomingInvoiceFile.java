package de.rechnio.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Unveraenderte Originaldatei einer eingegangenen E-Rechnung (GoBD-Aufbewahrung). */
@Entity
@Table(name = "incoming_invoice_file")
public class IncomingInvoiceFile {

    @Id
    @Column(name = "incoming_invoice_id")
    private UUID incomingInvoiceId;
    private String contentType;
    private byte[] content;

    protected IncomingInvoiceFile() {
    }

    public IncomingInvoiceFile(UUID incomingInvoiceId, String contentType, byte[] content) {
        this.incomingInvoiceId = incomingInvoiceId;
        this.contentType = contentType;
        this.content = content;
    }

    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
