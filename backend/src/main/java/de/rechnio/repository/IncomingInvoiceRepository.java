package de.rechnio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.rechnio.entity.IncomingInvoice;

public interface IncomingInvoiceRepository extends JpaRepository<IncomingInvoice, UUID> {

    List<IncomingInvoice> findByOrganizationIdOrderByReceivedAtDesc(UUID organizationId);

    Optional<IncomingInvoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<IncomingInvoice> findByOrganizationIdAndSha256(UUID organizationId, String sha256);
}
