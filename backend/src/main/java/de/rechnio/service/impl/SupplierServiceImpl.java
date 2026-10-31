package de.rechnio.service.impl;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.entity.IncomingInvoice;
import de.rechnio.entity.Supplier;
import de.rechnio.repository.SupplierRepository;
import de.rechnio.service.SupplierService;

@Service
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository suppliers;

    public SupplierServiceImpl(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Supplier assign(IncomingInvoice inv) {
        String key = matchKey(inv);
        Supplier supplier = suppliers.findByOrganizationIdAndMatchKey(inv.getOrganizationId(), key)
                .orElseGet(() -> suppliers.saveAndFlush(new Supplier(inv.getOrganizationId(),
                        suppliers.maxSupplierNumber(inv.getOrganizationId()) + 1, key,
                        inv.getSupplierName(), inv.getSupplierVatId(), inv.getSupplierTaxId())));
        inv.setSupplierId(supplier.getId());
        return supplier;
    }

    static String matchKey(IncomingInvoice inv) {
        if (notBlank(inv.getSupplierVatId())) return "VAT:" + normalize(inv.getSupplierVatId());
        if (notBlank(inv.getSupplierTaxId())) return "TAX:" + normalize(inv.getSupplierTaxId());
        if (notBlank(inv.getSupplierName())) {
            return "NAME:" + inv.getSupplierName().trim().toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ");
        }
        return "UNKNOWN";
    }

    private static String normalize(String id) {
        return id.replaceAll("[\\s./-]", "").toUpperCase(Locale.ROOT);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
