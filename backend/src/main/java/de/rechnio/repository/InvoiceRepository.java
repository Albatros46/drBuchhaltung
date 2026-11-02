package de.rechnio.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.rechnio.entity.Invoice;
import de.rechnio.enums.DocType;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

    List<Invoice> findByOrganizationIdAndDocTypeInAndIssueDateBetweenOrderByIssueDateAscNumberAsc(
            UUID organizationId, List<DocType> types, LocalDate from, LocalDate to);
}
