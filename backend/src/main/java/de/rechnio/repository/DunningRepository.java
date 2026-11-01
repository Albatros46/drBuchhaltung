package de.rechnio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.rechnio.entity.Dunning;

public interface DunningRepository extends JpaRepository<Dunning, UUID> {

    /** Nach Stufe sortiert (Stufen werden nur aufsteigend angelegt, daher genuegt das Datum). */
    List<Dunning> findByInvoiceIdAndOrganizationIdOrderByCreatedAtAsc(UUID invoiceId, UUID organizationId);

    Optional<Dunning> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
