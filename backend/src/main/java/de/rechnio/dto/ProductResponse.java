package de.rechnio.dto;

import java.math.BigDecimal;
import java.util.UUID;

import de.rechnio.entity.Product;

public record ProductResponse(UUID id, int productNumber, String name, String description, String unit,
                              BigDecimal unitPrice, BigDecimal vatRate) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getProductNumber(), p.getName(), p.getDescription(), p.getUnit(),
                p.getUnitPrice(), p.getVatRate());
    }
}
