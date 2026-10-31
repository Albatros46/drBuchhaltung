package de.rechnio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.rechnio.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByOrganizationIdAndMatchKey(UUID organizationId, String matchKey);

    List<Supplier> findByOrganizationId(UUID organizationId);

    @Query("select coalesce(max(s.supplierNumber), 0) from Supplier s where s.organizationId = ?1")
    int maxSupplierNumber(UUID organizationId);
}
