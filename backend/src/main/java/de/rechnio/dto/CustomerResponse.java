package de.rechnio.dto;

import java.util.UUID;

import de.rechnio.entity.Customer;

public record CustomerResponse(UUID id, int customerNumber, String name, String street, String zip, String city,
                               String country, String email, String ustId, String leitwegId) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getCustomerNumber(), c.getName(), c.getStreet(), c.getZip(),
                c.getCity(), c.getCountry(), c.getEmail(), c.getUstId(), c.getLeitwegId());
    }
}
