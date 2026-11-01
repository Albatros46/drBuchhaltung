package de.rechnio.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.rechnio.entity.OrganizationLogo;

public interface OrganizationLogoRepository extends JpaRepository<OrganizationLogo, UUID> {
}
