package de.rechnio.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(@NotBlank String name, String street, String zip, String city, String country,
                              String email, String ustId, String leitwegId) {
}
