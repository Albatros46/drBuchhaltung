package de.rechnio.service.impl;

import static de.rechnio.util.Csv.cell;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.ProductRequest;
import de.rechnio.dto.ProductResponse;
import de.rechnio.entity.Product;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.ProductRepository;
import de.rechnio.service.ProductService;
import de.rechnio.util.Csv;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository products;

    public ProductServiceImpl(ProductRepository products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> list(CurrentUser user) {
        return products.findByOrganizationIdOrderByName(user.organizationId()).stream().map(ProductResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse get(CurrentUser user, UUID id) {
        return ProductResponse.from(load(user, id));
    }

    @Override
    @Transactional
    public ProductResponse create(CurrentUser user, ProductRequest req) {
        UUID orgId = user.organizationId();
        Product p = new Product(orgId, products.maxProductNumber(orgId) + 1);
        apply(p, req);
        return ProductResponse.from(products.save(p));
    }

    @Override
    @Transactional
    public ProductResponse update(CurrentUser user, UUID id, ProductRequest req) {
        Product p = load(user, id);
        apply(p, req);
        return ProductResponse.from(p);
    }

    @Override
    @Transactional
    public void delete(CurrentUser user, UUID id) {
        products.delete(load(user, id));
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(CurrentUser user) {
        StringBuilder csv = new StringBuilder("Artikelnummer;Bezeichnung;Beschreibung;Einheit;Einzelpreis netto;USt %\r\n");
        for (Product p : products.findByOrganizationIdOrderByName(user.organizationId())) {
            csv.append(String.join(";", String.valueOf(p.getProductNumber()), cell(p.getName()),
                    cell(p.getDescription()), cell(p.getUnit()), Csv.money(p.getUnitPrice()),
                    Csv.number(p.getVatRate()))).append("\r\n");
        }
        return csv.toString();
    }

    private static void apply(Product p, ProductRequest req) {
        p.setName(req.name().strip());
        p.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().strip());
        p.setUnit(req.unit().strip());
        p.setUnitPrice(req.unitPrice());
        p.setVatRate(req.vatRate());
    }

    private Product load(CurrentUser user, UUID id) {
        return products.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Artikel"));
    }
}
