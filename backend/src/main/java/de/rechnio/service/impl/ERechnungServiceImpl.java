package de.rechnio.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.Profile;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.entity.Customer;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.InvoiceLine;
import de.rechnio.entity.Organization;
import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.enums.TaxMode;
import de.rechnio.exception.ApiException;
import de.rechnio.exception.InvoiceValidationException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.ERechnungService;
import de.rechnio.service.InvoicePdfService;
import de.rechnio.util.UnitCodes;

@Service
public class ERechnungServiceImpl implements ERechnungService {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String KLEINUNTERNEHMER_REASON = "Kleinunternehmer gemäß § 19 UStG";

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final InvoicePdfService invoicePdfService;

    public ERechnungServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                                OrganizationRepository organizations, InvoicePdfService invoicePdfService) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.invoicePdfService = invoicePdfService;
    }

    private record Source(Invoice invoice, Organization org, Customer customer, Invoice cancelled) {
    }

    @Override
    @Transactional(readOnly = true)
    public GeneratedFile xrechnung(CurrentUser user, UUID invoiceId) {
        Source src = load(user, invoiceId);
        require(missingFields(src, true));
        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName("XRechnung"));
        provider.generateXML(toMustang(src));
        return new GeneratedFile("XRechnung-" + safe(src.invoice().getNumber()) + ".xml", "application/xml",
                provider.getXML());
    }

    @Override
    @Transactional(readOnly = true)
    public GeneratedFile zugferd(CurrentUser user, UUID invoiceId) {
        Source src = load(user, invoiceId);
        require(missingFields(src, false));
        byte[] pdfA3 = invoicePdfService.render(user, invoiceId).content();
        Profile profile = Profiles.getByName("EN16931");

        ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()
                .setProducer("Rechnio")
                .setCreator(src.org().getName())
                .setProfile(profile);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            exporter.load(pdfA3);
            exporter.setTransaction(toMustang(src));
            exporter.export(out);
            String title = src.invoice().getDocType() == DocType.STORNO ? "Stornorechnung" : "Rechnung";
            return new GeneratedFile(title + "-" + safe(src.invoice().getNumber()) + "-ZUGFeRD.pdf",
                    "application/pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("ZUGFeRD-PDF konnte nicht erzeugt werden", e);
        } finally {
            try {
                exporter.close();
            } catch (IOException ignored) {
                // Dokument ist bereits geschrieben oder nie geladen worden
            }
        }
    }

    private Source load(CurrentUser user, UUID invoiceId) {
        Invoice inv = invoices.findByIdAndOrganizationId(invoiceId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Beleg"));
        if (inv.getDocType() == DocType.ANGEBOT) {
            throw ApiException.conflict("Für Angebote gibt es keine E-Rechnung");
        }
        if (inv.getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.conflict("E-Rechnungen gibt es erst nach dem Ausstellen");
        }
        Organization org = organizations.findById(user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Firma"));
        Customer customer = customers.findByIdAndOrganizationId(inv.getCustomerId(), user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Kunde"));
        Invoice cancelled = inv.getCancelsInvoiceId() == null ? null
                : invoices.findByIdAndOrganizationId(inv.getCancelsInvoiceId(), user.organizationId()).orElse(null);
        return new Source(inv, org, customer, cancelled);
    }

    /** Angaben, die EN 16931 bzw. zusaetzlich XRechnung verlangen und die §14 UStG noch nicht abdeckt. */
    static List<String> missingFields(Source src, boolean xrechnung) {
        Organization org = src.org();
        Customer customer = src.customer();
        List<String> errors = new ArrayList<>();
        if (src.invoice().getTaxMode() == TaxMode.REVERSE_CHARGE && isBlank(org.getUstId())) {
            errors.add("Eigene USt-IdNr. fehlt (Pflicht bei Reverse-Charge)");
        }
        if (xrechnung) {
            if (isBlank(org.getContactName())) errors.add("Ansprechpartner der Firma fehlt (Firma → Kontakt)");
            if (isBlank(org.getPhone())) errors.add("Telefonnummer der Firma fehlt (Firma → Kontakt)");
            if (isBlank(org.getEmail())) errors.add("E-Mail-Adresse der Firma fehlt (Firma → Kontakt)");
            if (isBlank(customer.getEmail())) errors.add("E-Mail-Adresse des Kunden fehlt (Kunden)");
            if (src.invoice().getDocType() == DocType.RECHNUNG && isBlank(org.getIban())) {
                errors.add("IBAN der Firma fehlt (Firma → Bank)");
            }
        }
        return errors;
    }

    private static void require(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new InvoiceValidationException("Angaben für die E-Rechnung fehlen", errors);
        }
    }

    /** Baut das Mustang-Datenmodell; ein Storno wird als Gutschrift (381) mit positiven Betraegen abgebildet. */
    static org.mustangproject.Invoice toMustang(Source src) {
        Invoice inv = src.invoice();
        Organization org = src.org();
        Customer customer = src.customer();
        boolean creditNote = inv.getDocType() == DocType.STORNO;

        TradeParty seller = new TradeParty(org.getName(), org.getStreet(), org.getZip(), org.getCity(), org.getCountry());
        if (!isBlank(org.getSteuernummer())) seller.addTaxID(org.getSteuernummer());
        if (!isBlank(org.getUstId())) {
            seller.addVATID(org.getUstId());
        } else if (!isBlank(org.getSteuernummer())) {
            // BR-CO-26 verlangt BT-29, BT-30 oder BT-31. Ohne USt-IdNr. (haeufig bei Kleinunternehmern)
            // dient die Steuernummer zusaetzlich als Verkaeuferkennung (BT-29).
            seller.setID(org.getSteuernummer());
        }
        if (!isBlank(org.getEmail())) seller.setEmail(org.getEmail());
        if (!isBlank(org.getContactName()) || !isBlank(org.getPhone())) {
            seller.setContact(new Contact(org.getContactName(), org.getPhone(), org.getEmail()));
        }
        if (!isBlank(org.getIban())) {
            seller.addBankDetails(isBlank(org.getBic()) ? new BankDetails(org.getIban())
                    : new BankDetails(org.getIban(), org.getBic()));
        }

        TradeParty buyer = new TradeParty(customer.getName(), customer.getStreet(), customer.getZip(),
                customer.getCity(), customer.getCountry());
        buyer.setID(String.valueOf(customer.getCustomerNumber()));
        if (!isBlank(customer.getEmail())) buyer.setEmail(customer.getEmail());
        if (!isBlank(customer.getUstId())) buyer.addVATID(customer.getUstId());

        org.mustangproject.Invoice mi = new org.mustangproject.Invoice()
                .setNumber(inv.getNumber())
                .setIssueDate(date(inv.getIssueDate()))
                .setDeliveryDate(date(inv.getServiceDateFrom() != null ? inv.getServiceDateFrom() : inv.getIssueDate()))
                .setSender(seller)
                .setRecipient(buyer)
                .setCurrency("EUR")
                // BT-10 Kaeuferreferenz: Leitweg-ID bei oeffentlichen Auftraggebern, sonst die Kundennummer
                .setReferenceNumber(isBlank(customer.getLeitwegId())
                        ? "Kd-Nr. " + customer.getCustomerNumber() : customer.getLeitwegId());
        // Zahlungsbedingungen immer fuellen: Mustang schreibt sonst ein leeres Element (PEPPOL-EN16931-R008)
        if (inv.getDueDate() != null) {
            mi.setDueDate(date(inv.getDueDate()))
                    .setPaymentTermDescription("Zahlbar bis " + DATE.format(inv.getDueDate()) + " ohne Abzug");
        } else if (creditNote) {
            mi.setPaymentTermDescription("Der Betrag wird erstattet bzw. verrechnet.");
        } else {
            mi.setPaymentTermDescription("Zahlbar sofort ohne Abzug");
        }
        if (creditNote) {
            mi.setCreditNote();
            if (src.cancelled() != null) {
                mi.setInvoiceReferencedDocumentID(src.cancelled().getNumber());
                mi.setInvoiceReferencedIssueDate(date(src.cancelled().getIssueDate()));
            }
        }

        for (InvoiceLine l : inv.getLines()) {
            Product product = new Product(l.getDescription(), "", UnitCodes.of(l.getUnit()), l.getVatRate());
            switch (inv.getTaxMode()) {
                case KLEINUNTERNEHMER -> product.setTaxCategoryCode("E").setTaxExemptionReason(KLEINUNTERNEHMER_REASON);
                case REVERSE_CHARGE -> product.setReverseCharge();
                case STANDARD -> {
                    if (l.getVatRate().signum() == 0) product.setTaxCategoryCode("Z");
                }
            }
            BigDecimal quantity = creditNote ? l.getQuantity().negate() : l.getQuantity();
            mi.addItem(new Item(product, l.getUnitPrice(), quantity));
        }
        return mi;
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.from(d.atStartOfDay(BERLIN).toInstant());
    }

    private static String safe(String s) {
        return s == null ? "Entwurf" : s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
