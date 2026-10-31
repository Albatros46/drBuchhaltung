package de.rechnio.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.rechnio.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
