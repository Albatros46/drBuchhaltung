package de.rechnio.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(@NotBlank @Size(max = 200) String name,
                             @Size(max = 1000) String description,
                             @NotBlank @Size(max = 20) String unit,
                             @NotNull @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
                             @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate) {
}
