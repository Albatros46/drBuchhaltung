package de.rechnio.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import de.rechnio.enums.Kontenrahmen;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private UUID id;
    private String name;
    private String street;
    private String zip;
    private String city;
    private String country = "DE";
    private String steuernummer;
    private String ustId;
    private boolean kleinunternehmer;
    @Enumerated(EnumType.STRING)
    private Kontenrahmen kontenrahmen = Kontenrahmen.SKR03;
    private String iban;
    private String bic;
    private String email;
    private String contactName;
    private String phone;
    /** Mahngebuehr je Mahnung ab der 1. Mahnung (Zahlungserinnerung kostenlos). */
    private BigDecimal dunningFee = BigDecimal.ZERO;
    private Integer datevBeraterNr;
    private Integer datevMandantNr;
    private Instant createdAt;

    protected Organization() {
    }

    public Organization(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getSteuernummer() { return steuernummer; }
    public void setSteuernummer(String steuernummer) { this.steuernummer = steuernummer; }
    public String getUstId() { return ustId; }
    public void setUstId(String ustId) { this.ustId = ustId; }
    public boolean isKleinunternehmer() { return kleinunternehmer; }
    public void setKleinunternehmer(boolean kleinunternehmer) { this.kleinunternehmer = kleinunternehmer; }
    public Kontenrahmen getKontenrahmen() { return kontenrahmen; }
    public void setKontenrahmen(Kontenrahmen kontenrahmen) { this.kontenrahmen = kontenrahmen; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getBic() { return bic; }
    public void setBic(String bic) { this.bic = bic; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public BigDecimal getDunningFee() { return dunningFee; }
    public void setDunningFee(BigDecimal dunningFee) { this.dunningFee = dunningFee; }
    public Integer getDatevBeraterNr() { return datevBeraterNr; }
    public void setDatevBeraterNr(Integer datevBeraterNr) { this.datevBeraterNr = datevBeraterNr; }
    public Integer getDatevMandantNr() { return datevMandantNr; }
    public void setDatevMandantNr(Integer datevMandantNr) { this.datevMandantNr = datevMandantNr; }
}
