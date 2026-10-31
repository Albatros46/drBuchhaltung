package de.rechnio.service;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.OrganizationDto;

public interface OrganizationService {

    OrganizationDto get(CurrentUser user);

    OrganizationDto update(CurrentUser user, OrganizationDto dto);
}
