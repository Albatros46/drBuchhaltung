package de.rechnio.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private UUID id;
    private UUID organizationId;
    /** Kundennummer; im DATEV-Export als Debitorenkonto 10000 + Nummer verwendet. */
    private int customerNumber;
    private String name;
    private String street;
    private String zip;
    private String city;
    private String country = "DE";
    private String email;
    private String ustId;
    private String leitwegId;
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(UUID organizationId, int customerNumber, String name) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.customerNumber = customerNumber;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public int getCustomerNumber() { return customerNumber; }
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
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUstId() { return ustId; }
    public void setUstId(String ustId) { this.ustId = ustId; }
    public String getLeitwegId() { return leitwegId; }
    public void setLeitwegId(String leitwegId) { this.leitwegId = leitwegId; }
}
