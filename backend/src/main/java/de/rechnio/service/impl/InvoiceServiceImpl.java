package de.rechnio.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.InvoiceDraftRequest;
import de.rechnio.dto.InvoiceLineRequest;
import de.rechnio.dto.InvoiceResponse;
import de.rechnio.entity.AuditLog;
import de.rechnio.entity.Customer;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.InvoiceLine;
import de.rechnio.entity.Organization;
import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.enums.TaxMode;
import de.rechnio.exception.ApiException;
import de.rechnio.exception.InvoiceValidationException;
import de.rechnio.repository.AuditLogRepository;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.InvoiceService;
import de.rechnio.service.NumberingService;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Set<BigDecimal> ALLOWED_RATES = Set.of(
            new BigDecimal("0"), new BigDecimal("7"), new BigDecimal("19"));

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final NumberingService numbering;
    private final AuditLogRepository audit;
    private final Clock clock;

    public InvoiceServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                              OrganizationRepository organizations, NumberingService numbering,
                              AuditLogRepository audit, Clock clock) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(CurrentUser user) {
        return invoices.findByOrganizationIdOrderByCreatedAtDesc(user.organizationId())
                .stream().map(InvoiceResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse get(CurrentUser user, UUID id) {
        return InvoiceResponse.from(load(user, id));
    }

    @Override
    @Transactional
    public InvoiceResponse createDraft(CurrentUser user, InvoiceDraftRequest req) {
        DocType type = req.docType() == null ? DocType.RECHNUNG : req.docType();
        if (type == DocType.STORNO) {
            throw ApiException.badRequest("Stornorechnungen entstehen nur ueber 'Stornieren'");
        }
        Organization org = organization(user);
        customer(user, req.customerId());
        Invoice inv = new Invoice(user.organizationId(), req.customerId(), type, effectiveTaxMode(org, req.taxMode()));
        applyDraft(inv, req);
        invoices.save(inv);
        log(user, inv, "CREATED", null);
        return InvoiceResponse.from(inv);
    }

    @Override
    @Transactional
    public InvoiceResponse updateDraft(CurrentUser user, UUID id, InvoiceDraftRequest req) {
        Invoice inv = load(user, id);
        requireDraft(inv);
        Organization org = organization(user);
        customer(user, req.customerId());
        inv.setCustomerId(req.customerId());
        inv.setTaxMode(effectiveTaxMode(org, req.taxMode()));
        applyDraft(inv, req);
        log(user, inv, "UPDATED", null);
        return InvoiceResponse.from(inv);
    }

    @Override
    @Transactional
    public void deleteDraft(CurrentUser user, UUID id) {
        Invoice inv = load(user, id);
        requireDraft(inv);
        invoices.delete(inv);
        log(user, inv, "DELETED", null);
    }

    @Override
    @Transactional
    public InvoiceResponse issue(CurrentUser user, UUID id) {
        Invoice inv = load(user, id);
        requireDraft(inv);
        Organization org = organization(user);
        Customer customer = customer(user, inv.getCustomerId());

        List<String> errors = missingMandatoryFields(org, customer, inv);
        if (!errors.isEmpty()) {
            throw new InvoiceValidationException(errors);
        }
        LocalDate today = LocalDate.now(clock);
        inv.lock(numbering.next(org.getId(), inv.getDocType(), today.getYear()), today, Instant.now(clock));
        if (inv.getDueDate() == null && inv.getDocType() == DocType.RECHNUNG) {
            inv.setDueDate(today.plusDays(14));
        }
        log(user, inv, "ISSUED", inv.getNumber());
        return InvoiceResponse.from(inv);
    }

    @Override
    @Transactional
    public InvoiceResponse markPaid(CurrentUser user, UUID id, LocalDate paidAt) {
        Invoice inv = load(user, id);
        if (inv.getDocType() != DocType.RECHNUNG || inv.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.conflict("Nur ausgestellte, offene Rechnungen koennen als bezahlt markiert werden");
        }
        inv.setStatus(InvoiceStatus.PAID);
        inv.setPaidAt(paidAt == null ? LocalDate.now(clock) : paidAt);
        log(user, inv, "PAID", inv.getPaidAt().toString());
        return InvoiceResponse.from(inv);
    }

    @Override
    @Transactional
    public InvoiceResponse cancel(CurrentUser user, UUID id) {
        Invoice original = load(user, id);
        if (original.getDocType() != DocType.RECHNUNG
                || (original.getStatus() != InvoiceStatus.ISSUED && original.getStatus() != InvoiceStatus.PAID)) {
            throw ApiException.conflict("Nur ausgestellte Rechnungen koennen storniert werden");
        }
        Invoice storno = new Invoice(user.organizationId(), original.getCustomerId(), DocType.STORNO, original.getTaxMode());
        storno.setCancelsInvoiceId(original.getId());
        storno.setServiceDateFrom(original.getServiceDateFrom());
        storno.setServiceDateTo(original.getServiceDateTo());
        storno.setIntroText("Stornorechnung zu Rechnung " + original.getNumber());
        for (InvoiceLine l : original.getLines()) {
            storno.addLine(l.getDescription(), l.getQuantity().negate(), l.getUnit(), l.getUnitPrice(), l.getVatRate());
        }
        LocalDate today = LocalDate.now(clock);
        storno.lock(numbering.next(user.organizationId(), DocType.STORNO, today.getYear()), today, Instant.now(clock));
        invoices.save(storno);

        original.setStatus(InvoiceStatus.CANCELLED);
        log(user, original, "CANCELLED", storno.getNumber());
        log(user, storno, "ISSUED", storno.getNumber());
        return InvoiceResponse.from(storno);
    }

    @Override
    @Transactional
    public InvoiceResponse convertToInvoice(CurrentUser user, UUID offerId) {
        Invoice offer = load(user, offerId);
        if (offer.getDocType() != DocType.ANGEBOT) {
            throw ApiException.conflict("Nur Angebote koennen in eine Rechnung umgewandelt werden");
        }
        Invoice inv = new Invoice(user.organizationId(), offer.getCustomerId(), DocType.RECHNUNG, offer.getTaxMode());
        inv.setIntroText(offer.getIntroText());
        inv.setServiceDateFrom(offer.getServiceDateFrom());
        inv.setServiceDateTo(offer.getServiceDateTo());
        for (InvoiceLine l : offer.getLines()) {
            inv.addLine(l.getDescription(), l.getQuantity(), l.getUnit(), l.getUnitPrice(), l.getVatRate());
        }
        invoices.save(inv);
        log(user, inv, "CREATED", "aus Angebot " + offer.getNumber());
        return InvoiceResponse.from(inv);
    }

    /** Pflichtangaben nach §14 UStG. */
    private static List<String> missingMandatoryFields(Organization org, Customer customer, Invoice inv) {
        List<String> errors = new ArrayList<>();
        if (isBlank(org.getName()) || isBlank(org.getStreet()) || isBlank(org.getZip()) || isBlank(org.getCity())) {
            errors.add("Vollstaendige Anschrift des Unternehmens fehlt");
        }
        if (isBlank(org.getSteuernummer()) && isBlank(org.getUstId())) {
            errors.add("Steuernummer oder USt-IdNr. des Unternehmens fehlt");
        }
        if (isBlank(customer.getName()) || isBlank(customer.getStreet()) || isBlank(customer.getZip())
                || isBlank(customer.getCity())) {
            errors.add("Vollstaendige Anschrift des Kunden fehlt");
        }
        if (inv.getLines().isEmpty()) {
            errors.add("Mindestens eine Position ist erforderlich");
        }
        if (inv.getDocType() == DocType.RECHNUNG && inv.getServiceDateFrom() == null) {
            errors.add("Leistungsdatum oder Leistungszeitraum fehlt");
        }
        if (inv.getTaxMode() == TaxMode.REVERSE_CHARGE && isBlank(customer.getUstId())) {
            errors.add("USt-IdNr. des Kunden ist bei Reverse-Charge (§13b UStG) erforderlich");
        }
        if (inv.getGrossTotal().signum() < 0) {
            errors.add("Rechnungsbetrag darf nicht negativ sein (dafuer 'Stornieren' verwenden)");
        }
        return errors;
    }

    private static void applyDraft(Invoice inv, InvoiceDraftRequest req) {
        inv.setServiceDateFrom(req.serviceDateFrom());
        inv.setServiceDateTo(req.serviceDateTo());
        inv.setDueDate(req.dueDate());
        inv.setIntroText(req.introText());
        inv.clearLines();
        for (InvoiceLineRequest l : req.lines()) {
            if (ALLOWED_RATES.stream().noneMatch(r -> r.compareTo(l.vatRate()) == 0)) {
                throw ApiException.badRequest("Unzulaessiger Steuersatz: " + l.vatRate() + " (erlaubt: 0, 7, 19)");
            }
            inv.addLine(l.description(), l.quantity(), l.unit(), l.unitPrice(), l.vatRate());
        }
    }

    private static TaxMode effectiveTaxMode(Organization org, TaxMode requested) {
        if (org.isKleinunternehmer()) {
            return TaxMode.KLEINUNTERNEHMER;
        }
        if (requested == TaxMode.KLEINUNTERNEHMER) {
            throw ApiException.badRequest("Das Unternehmen ist nicht als Kleinunternehmer (§19 UStG) eingestellt");
        }
        return requested == null ? TaxMode.STANDARD : requested;
    }

    private static void requireDraft(Invoice inv) {
        if (inv.isLocked()) {
            throw ApiException.conflict("Beleg " + inv.getNumber() + " ist festgeschrieben und kann nicht geaendert werden");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Invoice load(CurrentUser user, UUID id) {
        return invoices.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Beleg"));
    }

    private Organization organization(CurrentUser user) {
        return organizations.findById(user.organizationId()).orElseThrow(() -> ApiException.notFound("Firma"));
    }

    private Customer customer(CurrentUser user, UUID customerId) {
        if (customerId == null) {
            throw ApiException.badRequest("Kunde ist erforderlich");
        }
        return customers.findByIdAndOrganizationId(customerId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Kunde"));
    }

    private void log(CurrentUser user, Invoice inv, String action, String detail) {
        audit.save(new AuditLog(user.organizationId(), "INVOICE", inv.getId(), action, detail, user.email()));
    }
}
