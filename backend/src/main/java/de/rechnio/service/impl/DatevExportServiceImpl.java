package de.rechnio.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.entity.Customer;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.Organization;
import de.rechnio.enums.DocType;
import de.rechnio.enums.Kontenrahmen;
import de.rechnio.enums.TaxMode;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.DatevExportService;
import de.rechnio.util.VatCalculator;

/**
 * Erzeugt einen DATEV-Buchungsstapel im EXTF-Format (Version 700) fuer die Ausgangsrechnungen eines Zeitraums.
 * Je Rechnung und Steuersatz entsteht eine Buchung: Debitor an Erloeskonto (Automatikkonto, Bruttobetrag).
 * Der Export sollte vor dem produktiven Einsatz einmal mit dem Steuerberater in DATEV probeweise importiert werden.
 */
@Service
public class DatevExportServiceImpl implements DatevExportService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter BELEGDATUM = DateTimeFormatter.ofPattern("ddMM");
    private static final int DEBITOR_BASE = 10000;

    private static final String COLUMNS = String.join(";",
            "Umsatz (ohne Soll/Haben-Kz)", "Soll/Haben-Kennzeichen", "WKZ Umsatz", "Kurs", "Basis-Umsatz",
            "WKZ Basis-Umsatz", "Konto", "Gegenkonto (ohne BU-Schlüssel)", "BU-Schlüssel", "Belegdatum",
            "Belegfeld 1", "Belegfeld 2", "Skonto", "Buchungstext");

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final Clock clock;

    public DatevExportServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                                  OrganizationRepository organizations, Clock clock) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(UUID organizationId, LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.getYear() != to.getYear()) {
            throw ApiException.badRequest("Zeitraum muss innerhalb eines Wirtschaftsjahres liegen");
        }
        Organization org = organizations.findById(organizationId).orElseThrow(() -> ApiException.notFound("Firma"));
        Map<UUID, Customer> customerById = customers.findByOrganizationIdOrderByName(organizationId).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));

        List<String> lines = new ArrayList<>();
        lines.add(header(org, from, to));
        lines.add(COLUMNS);
        for (Invoice inv : invoices.findByOrganizationIdAndDocTypeInAndIssueDateBetweenOrderByIssueDateAscNumberAsc(
                organizationId, List.of(DocType.RECHNUNG, DocType.STORNO), from, to)) {
            Customer customer = customerById.get(inv.getCustomerId());
            for (VatCalculator.VatGroup group : VatCalculator.totals(inv.getLines()).groups()) {
                lines.add(booking(org, customer, inv, group));
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
                q("Ausgangsrechnungen " + from.format(YMD) + "-" + to.format(YMD)), q(""),
                "1", "0", "0", q("EUR"),
                "", q(""), "", "", q(skr), "", "", q(""), q("")));
        return String.join(";", f);
    }

    private static String booking(Organization org, Customer customer, Invoice inv, VatCalculator.VatGroup group) {
        BigDecimal gross = group.gross();
        String sollHaben = gross.signum() < 0 ? "H" : "S";
        int debitor = DEBITOR_BASE + (customer == null ? 0 : customer.getCustomerNumber());
        String text = customer == null ? "" : customer.getName();
        return String.join(";",
                amount(gross.abs()), q(sollHaben), q("EUR"), "", "", q(""),
                String.valueOf(debitor),
                String.valueOf(revenueAccount(org.getKontenrahmen(), inv.getTaxMode(), group.rate())),
                q(""), inv.getIssueDate().format(BELEGDATUM),
                q(inv.getNumber()), q(""), "", q(truncate(text, 60)));
    }

    /** Erloes-Automatikkonten (USt wird von DATEV aus dem Bruttobetrag herausgerechnet). */
    private static int revenueAccount(Kontenrahmen kontenrahmen, TaxMode mode, BigDecimal rate) {
        boolean skr04 = kontenrahmen == Kontenrahmen.SKR04;
        return switch (mode) {
            case KLEINUNTERNEHMER -> skr04 ? 4185 : 8195;
            case REVERSE_CHARGE -> skr04 ? 4337 : 8337;
            case STANDARD -> {
                int r = rate.intValue();
                if (r == 19) yield skr04 ? 4400 : 8400;
                if (r == 7) yield skr04 ? 4300 : 8300;
                yield skr04 ? 4100 : 8100;
            }
        };
    }

    private static String amount(BigDecimal v) {
        return v.setScale(2).toPlainString().replace('.', ',');
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
