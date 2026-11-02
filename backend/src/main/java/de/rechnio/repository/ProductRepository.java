package de.rechnio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.rechnio.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select coalesce(max(p.productNumber), 0) from Product p where p.organizationId = ?1")
    int maxProductNumber(UUID organizationId);
}
