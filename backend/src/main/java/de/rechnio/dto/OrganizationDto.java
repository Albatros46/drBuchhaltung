package de.rechnio.dto;

import de.rechnio.entity.Organization;
import de.rechnio.enums.Kontenrahmen;
import jakarta.validation.constraints.NotBlank;

/** Firmenstammdaten; dient als Anfrage und Antwort. */
public record OrganizationDto(
        @NotBlank String name,
        String street,
        String zip,
        String city,
        String country,
        String steuernummer,
        String ustId,
        boolean kleinunternehmer,
        Kontenrahmen kontenrahmen,
        String iban,
        String bic,
        String email,
        String contactName,
        String phone,
        Integer datevBeraterNr,
        Integer datevMandantNr) {

    public static OrganizationDto from(Organization o) {
        return new OrganizationDto(o.getName(), o.getStreet(), o.getZip(), o.getCity(), o.getCountry(),
                o.getSteuernummer(), o.getUstId(), o.isKleinunternehmer(), o.getKontenrahmen(),
                o.getIban(), o.getBic(), o.getEmail(), o.getContactName(), o.getPhone(),
                o.getDatevBeraterNr(), o.getDatevMandantNr());
    }
}
