package de.rechnio.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.rechnio.entity.IncomingInvoiceFile;

public interface IncomingInvoiceFileRepository extends JpaRepository<IncomingInvoiceFile, UUID> {
}
