package de.rechnio.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.entity.Customer;
import de.rechnio.entity.IncomingInvoice;
import de.rechnio.entity.IncomingInvoiceLine;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.Organization;
import de.rechnio.entity.Supplier;
import de.rechnio.enums.DocType;
import de.rechnio.enums.IncomingDocType;
import de.rechnio.enums.Kontenrahmen;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.IncomingInvoiceRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.repository.SupplierRepository;
import de.rechnio.service.DatevExportService;
import de.rechnio.service.SupplierService;
import de.rechnio.util.DatevAccounts;
import de.rechnio.util.Kontierung;
import de.rechnio.util.VatCalculator;

/**
 * Erzeugt einen DATEV-Buchungsstapel im EXTF-Format (Version 700) fuer einen Zeitraum.
 * <ul>
 *   <li>Ausgangsrechnungen: je Rechnung und Steuersatz Debitor (Soll) an Erloeskonto.</li>
 *   <li>Eingangsrechnungen: je Rechnung und Steuersatz Kreditor (Haben) an Wareneingangskonto.</li>
 * </ul>
 * Alle Gegenkonten sind Automatikkonten mit Bruttobetrag. Der Export sollte vor dem produktiven Einsatz
 * einmal mit dem Steuerberater in DATEV probeweise importiert werden.
 */
@Service
public class DatevExportServiceImpl implements DatevExportService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter BELEGDATUM = DateTimeFormatter.ofPattern("ddMM");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<BigDecimal> GERMAN_RATES = List.of(new BigDecimal("19"), new BigDecimal("7"), BigDecimal.ZERO);

    private static final String COLUMNS = String.join(";",
            "Umsatz (ohne Soll/Haben-Kz)", "Soll/Haben-Kennzeichen", "WKZ Umsatz", "Kurs", "Basis-Umsatz",
            "WKZ Basis-Umsatz", "Konto", "Gegenkonto (ohne BU-Schlüssel)", "BU-Schlüssel", "Belegdatum",
            "Belegfeld 1", "Belegfeld 2", "Skonto", "Buchungstext");

    private final InvoiceRepository invoices;
    private final IncomingInvoiceRepository incomingInvoices;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final SupplierService supplierService;
    private final OrganizationRepository organizations;
    private final Clock clock;

    public DatevExportServiceImpl(InvoiceRepository invoices, IncomingInvoiceRepository incomingInvoices,
                                  CustomerRepository customers, SupplierRepository suppliers,
                                  SupplierService supplierService, OrganizationRepository organizations, Clock clock) {
        this.invoices = invoices;
        this.incomingInvoices = incomingInvoices;
        this.customers = customers;
        this.suppliers = suppliers;
        this.supplierService = supplierService;
        this.organizations = organizations;
        this.clock = clock;
    }

    // Nicht readOnly: Eingangsrechnungen ohne Lieferant (vor Einfuehrung der Zuordnung) erhalten hier ihren Kreditor.
    @Override
    @Transactional
    public String exportCsv(UUID organizationId, LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.getYear() != to.getYear()) {
            throw ApiException.badRequest("Zeitraum muss innerhalb eines Wirtschaftsjahres liegen");
        }
        Organization org = organizations.findById(organizationId).orElseThrow(() -> ApiException.notFound("Firma"));
        Map<UUID, Customer> customerById = customers.findByOrganizationIdOrderByName(organizationId).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, Supplier> supplierById = suppliers.findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));

        List<String> lines = new ArrayList<>();
        lines.add(header(org, from, to));
        lines.add(COLUMNS);
        for (Invoice inv : invoices.findByOrganizationIdAndDocTypeInAndIssueDateBetweenOrderByIssueDateAscNumberAsc(
                organizationId, List.of(DocType.RECHNUNG, DocType.STORNO), from, to)) {
            Customer customer = customerById.get(inv.getCustomerId());
            for (VatCalculator.VatGroup group : VatCalculator.totals(inv.getLines()).groups()) {
                lines.add(outgoing(org, customer, inv, group));
            }
        }
        for (IncomingInvoice inv : incomingInvoices.findByOrganizationIdAndIssueDateBetweenOrderByIssueDateAscNumberAsc(
                organizationId, from, to)) {
            Supplier supplier = inv.getSupplierId() == null ? null : supplierById.get(inv.getSupplierId());
            if (supplier == null) {
                supplier = supplierService.assign(inv);
                supplierById.put(supplier.getId(), supplier);
            }
            for (RateAmount part : grossByRate(inv)) {
                lines.add(incoming(org, supplier, inv, part));
            }
        }
        return String.join("\r\n", lines) + "\r\n";
    }

    private String header(Organization org, LocalDate from, LocalDate to) {
        String skr = org.getKontenrahmen() == Kontenrahmen.SKR04 ? "04" : "03";
        List<String> f = new ArrayList<>(List.of(
                q("EXTF"), "700", "21", q("Buchungsstapel"), "13",
                LocalDateTime.now(clock).format(CREATED), "", q("RE"), q("Rechnio"), q(""),
                num(org.getDatevBeraterNr()), num(org.getDatevMandantNr()),
                LocalDate.of(from.getYear(), 1, 1).format(YMD), "4",
                from.format(YMD), to.format(YMD),
                q("Rechnungsausgang und -eingang " + from.format(YMD) + "-" + to.format(YMD)), q(""),
                "1", "0", "0", q("EUR"),
                "", q(""), "", "", q(skr), "", "", q(""), q("")));
        return String.join(";", f);
    }

    private static String outgoing(Organization org, Customer customer, Invoice inv, VatCalculator.VatGroup group) {
        BigDecimal gross = group.gross();
        String sollHaben = gross.signum() < 0 ? "H" : "S";
        int debitor = DatevAccounts.DEBITOR_BASE + (customer == null ? 0 : customer.getCustomerNumber());
        String text = customer == null ? "" : customer.getName();
        return String.join(";",
                amount(gross.abs()), q(sollHaben), q("EUR"), "", "", q(""),
                String.valueOf(debitor),
                String.valueOf(DatevAccounts.revenue(org.getKontenrahmen(), inv.getTaxMode(), group.rate())),
                q(""), inv.getIssueDate().format(BELEGDATUM),
                q(inv.getNumber()), q(""), "", q(truncate(text, 60)));
    }

    /** Kreditor im Haben (Verbindlichkeit), bei Gutschriften im Soll. */
    private static String incoming(Organization org, Supplier supplier, IncomingInvoice inv, RateAmount part) {
        boolean creditNote = inv.getDocumentType() == IncomingDocType.GUTSCHRIFT;
        String sollHaben = (part.gross().signum() < 0) != creditNote ? "S" : "H";
        int kreditor = DatevAccounts.KREDITOR_BASE + supplier.getSupplierNumber();
        String text = inv.getSupplierName() == null ? "" : inv.getSupplierName();
        return String.join(";",
                amount(part.gross().abs()), q(sollHaben), q("EUR"), "", "", q(""),
                String.valueOf(kreditor),
                String.valueOf(Kontierung.account(inv, org.getKontenrahmen(), part.rate())),
                q(Kontierung.buKey(inv, part.rate())), inv.getIssueDate().format(BELEGDATUM),
                q(belegfeld(inv.getNumber())), q(""), "", q(truncate(text, 60)));
    }

    record RateAmount(BigDecimal rate, BigDecimal gross) {
    }

    /**
     * Bruttobetrag je Steuersatz aus den Positionen. Rundungsdifferenzen zur Rechnungssumme des
     * Lieferanten werden der groessten Gruppe zugeschlagen, damit die Summe exakt der Rechnung entspricht.
     */
    static List<RateAmount> grossByRate(IncomingInvoice inv) {
        Map<BigDecimal, BigDecimal> netByRate = new TreeMap<>();
        for (IncomingInvoiceLine l : inv.getLines()) {
            if (l.getNetAmount() == null) continue;
            BigDecimal rate = l.getVatRate() == null ? inferRate(inv) : l.getVatRate().stripTrailingZeros();
            netByRate.merge(rate, l.getNetAmount(), BigDecimal::add);
        }
        if (netByRate.isEmpty()) {
            return List.of(new RateAmount(inferRate(inv), inv.getGrossTotal()));
        }
        List<RateAmount> parts = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        RateAmount largest = null;
        for (Map.Entry<BigDecimal, BigDecimal> e : netByRate.entrySet()) {
            BigDecimal vat = e.getValue().multiply(e.getKey()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            RateAmount part = new RateAmount(e.getKey(), e.getValue().add(vat).setScale(2, RoundingMode.HALF_UP));
            parts.add(part);
            sum = sum.add(part.gross());
            if (largest == null || part.gross().abs().compareTo(largest.gross().abs()) > 0) largest = part;
        }
        BigDecimal diff = inv.getGrossTotal().subtract(sum);
        if (diff.signum() != 0 && largest != null) {
            parts.set(parts.indexOf(largest), new RateAmount(largest.rate(), largest.gross().add(diff)));
        }
        return parts;
    }

    /** Steuersatz aus den Rechnungssummen, gerundet auf den naechsten deutschen Satz (19, 7 oder 0 %). */
    private static BigDecimal inferRate(IncomingInvoice inv) {
        if (inv.getNetTotal() == null || inv.getNetTotal().signum() == 0) return BigDecimal.ZERO;
        BigDecimal actual = inv.getVatTotal().multiply(HUNDRED).divide(inv.getNetTotal(), 2, RoundingMode.HALF_UP);
        BigDecimal best = BigDecimal.ZERO;
        for (BigDecimal r : GERMAN_RATES) {
            if (actual.subtract(r).abs().compareTo(actual.subtract(best).abs()) < 0) best = r;
        }
        return best;
    }

    /** DATEV erlaubt in Belegfeld 1 nur A-Z, a-z, 0-9 und $ % & * + - . / bei max. 36 Zeichen. */
    static String belegfeld(String number) {
        if (number == null) return "";
        return truncate(number.replaceAll("[^A-Za-z0-9$%&*+\\-./]", ""), 36);
    }

    private static String amount(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String num(Integer v) {
        return v == null ? "" : v.toString();
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
