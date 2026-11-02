package de.rechnio.service.impl;

import static de.rechnio.util.Csv.cell;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.CustomerRequest;
import de.rechnio.dto.CustomerResponse;
import de.rechnio.entity.Customer;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.service.CustomerService;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;

    public CustomerServiceImpl(CustomerRepository customers, InvoiceRepository invoices) {
        this.customers = customers;
        this.invoices = invoices;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> list(CurrentUser user) {
        return customers.findByOrganizationIdOrderByName(user.organizationId())
                .stream().map(CustomerResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(CurrentUser user, UUID id) {
        return CustomerResponse.from(load(user, id));
    }

    @Override
    @Transactional
    public CustomerResponse create(CurrentUser user, CustomerRequest req) {
        UUID orgId = user.organizationId();
        Customer c = new Customer(orgId, customers.maxCustomerNumber(orgId) + 1, req.name());
        apply(c, req);
        return CustomerResponse.from(customers.save(c));
    }

    @Override
    @Transactional
    public CustomerResponse update(CurrentUser user, UUID id, CustomerRequest req) {
        Customer c = load(user, id);
        c.setName(req.name());
        apply(c, req);
        return CustomerResponse.from(c);
    }

    @Override
    @Transactional
    public void delete(CurrentUser user, UUID id) {
        Customer c = load(user, id);
        if (invoices.existsByOrganizationIdAndCustomerId(user.organizationId(), id)) {
            throw ApiException.conflict("Der Kunde hat Belege und muss für die Aufbewahrung erhalten bleiben. "
                    + "Entwürfe können vorher gelöscht werden.");
        }
        customers.delete(c);
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(CurrentUser user) {
        StringBuilder csv = new StringBuilder("Kundennummer;Name;Straße;PLZ;Ort;Land;E-Mail;USt-IdNr.;Leitweg-ID\r\n");
        for (Customer c : customers.findByOrganizationIdOrderByName(user.organizationId())) {
            csv.append(String.join(";", String.valueOf(c.getCustomerNumber()), cell(c.getName()), cell(c.getStreet()),
                    cell(c.getZip()), cell(c.getCity()), cell(c.getCountry()), cell(c.getEmail()), cell(c.getUstId()),
                    cell(c.getLeitwegId()))).append("\r\n");
        }
        return csv.toString();
    }

    private static void apply(Customer c, CustomerRequest req) {
        c.setStreet(req.street());
        c.setZip(req.zip());
        c.setCity(req.city());
        c.setCountry(req.country() == null ? "DE" : req.country());
        c.setEmail(req.email());
        c.setUstId(req.ustId());
        c.setLeitwegId(req.leitwegId());
    }

    private Customer load(CurrentUser user, UUID id) {
        return customers.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Kunde"));
    }
}
