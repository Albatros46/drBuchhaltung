package de.rechnio.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.OrganizationDto;
import de.rechnio.entity.Organization;
import de.rechnio.enums.Kontenrahmen;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.OrganizationService;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizations;

    public OrganizationServiceImpl(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDto get(CurrentUser user) {
        return OrganizationDto.from(load(user));
    }

    @Override
    @Transactional
    public OrganizationDto update(CurrentUser user, OrganizationDto dto) {
        Organization o = load(user);
        o.setName(dto.name());
        o.setStreet(dto.street());
        o.setZip(dto.zip());
        o.setCity(dto.city());
        o.setCountry(dto.country() == null ? "DE" : dto.country());
        o.setSteuernummer(dto.steuernummer());
        o.setUstId(dto.ustId());
        o.setKleinunternehmer(dto.kleinunternehmer());
        o.setKontenrahmen(dto.kontenrahmen() == null ? Kontenrahmen.SKR03 : dto.kontenrahmen());
        o.setIban(dto.iban());
        o.setBic(dto.bic());
        o.setEmail(dto.email());
        o.setContactName(dto.contactName());
        o.setPhone(dto.phone());
        o.setDunningFee(dto.dunningFee() == null ? BigDecimal.ZERO : dto.dunningFee());
        o.setDatevBeraterNr(dto.datevBeraterNr());
        o.setDatevMandantNr(dto.datevMandantNr());
        return OrganizationDto.from(o);
    }

    private Organization load(CurrentUser user) {
        return organizations.findById(user.organizationId()).orElseThrow(() -> ApiException.notFound("Firma"));
    }
}
