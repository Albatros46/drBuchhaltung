package de.rechnio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.rechnio.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<Customer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select coalesce(max(c.customerNumber), 0) from Customer c where c.organizationId = ?1")
    int maxCustomerNumber(UUID organizationId);
}
