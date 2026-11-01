package de.rechnio.service;

import java.util.Optional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.LogoResponse;
import de.rechnio.dto.OrganizationDto;

public interface OrganizationService {

    OrganizationDto get(CurrentUser user);

    OrganizationDto update(CurrentUser user, OrganizationDto dto);

    Optional<LogoResponse> logo(CurrentUser user);

    /**
     * Speichert das Firmenlogo. Akzeptiert PNG und JPEG bis 1 MB; das Bild wird neu kodiert
     * (Metadaten entfernt) und auf hoechstens 1200 px verkleinert.
     */
    LogoResponse uploadLogo(CurrentUser user, byte[] content);

    void deleteLogo(CurrentUser user);
}
