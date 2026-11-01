package de.rechnio.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.entity.Customer;
import de.rechnio.entity.IncomingInvoice;
import de.rechnio.entity.Organization;
import de.rechnio.entity.Supplier;
import de.rechnio.enums.Kontenrahmen;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.IncomingInvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.repository.SupplierRepository;
import de.rechnio.service.DatevStammdatenService;
import de.rechnio.service.SupplierService;
import de.rechnio.util.DatevAccounts;

/**
 * DATEV-Format Debitoren/Kreditoren (EXTF, Formatkategorie 16, Version 5) mit 254 Feldern je Zeile.
 * Gefuellt werden Konto, Name, Kurzbezeichnung, USt-IdNr., Anschrift, E-Mail, IBAN, Kunden- bzw.
 * Lieferantennummer und Steuernummer. Lieferanten haben keine eigene Anschrift: sie stammt aus ihrer
 * zuletzt eingegangenen Rechnung.
 */
@Service
public class DatevStammdatenServiceImpl implements DatevStammdatenService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final List<String> COLUMNS = columns();

    // Feldnummern laut DATEV-Formatbeschreibung (1-basiert)
    private static final int KONTO = 1;
    private static final int NAME_KEINE_ANGABE = 6;
    private static final int ADRESSATENTYP = 7;
    private static final int KURZBEZEICHNUNG = 8;
    private static final int EU_LAND = 9;
    private static final int EU_USTID = 10;
    private static final int ADRESSART = 15;
    private static final int STRASSE = 16;
    private static final int PLZ = 18;
    private static final int ORT = 19;
    private static final int LAND = 20;
    private static final int EMAIL = 33;
    private static final int IBAN = 45;
    private static final int HAUPTBANK = 49;
    private static final int KUNDENNUMMER = 99;
    private static final int STEUERNUMMER = 100;

    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final IncomingInvoiceRepository incomingInvoices;
    private final SupplierService supplierService;
    private final OrganizationRepository organizations;
    private final Clock clock;

    public DatevStammdatenServiceImpl(CustomerRepository customers, SupplierRepository suppliers,
                                      IncomingInvoiceRepository incomingInvoices, SupplierService supplierService,
                                      OrganizationRepository organizations, Clock clock) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.incomingInvoices = incomingInvoices;
        this.supplierService = supplierService;
        this.organizations = organizations;
        this.clock = clock;
    }

    // Nicht readOnly: Eingangsrechnungen ohne Lieferant erhalten hier ihren Kreditor (wie im Buchungsstapel).
    @Override
    @Transactional
    public String exportCsv(UUID organizationId) {
        Organization org = organizations.findById(organizationId).orElseThrow(() -> ApiException.notFound("Firma"));
        // neueste Rechnung je Lieferant liefert Anschrift und Bankverbindung
        Map<UUID, IncomingInvoice> latest = new HashMap<>();
        for (IncomingInvoice inv : incomingInvoices.findByOrganizationIdOrderByReceivedAtDesc(organizationId)) {
            UUID supplierId = inv.getSupplierId() != null ? inv.getSupplierId() : supplierService.assign(inv).getId();
            latest.putIfAbsent(supplierId, inv);
        }

        List<String> lines = new ArrayList<>();
        lines.add(header(org));
        lines.add(COLUMNS.stream().collect(Collectors.joining(";")));
        for (Customer c : customers.findByOrganizationIdOrderByName(organizationId)) {
            lines.add(debitor(c));
        }
        suppliers.findByOrganizationId(organizationId).stream()
                .sorted((a, b) -> Integer.compare(a.getSupplierNumber(), b.getSupplierNumber()))
                .forEach(s -> lines.add(kreditor(s, latest.get(s.getId()))));
        return String.join("\r\n", lines) + "\r\n";
    }

    private String header(Organization org) {
        String skr = org.getKontenrahmen() == Kontenrahmen.SKR04 ? "04" : "03";
        LocalDate today = LocalDate.now(clock);
        List<String> f = new ArrayList<>(List.of(
                q("EXTF"), "700", "16", q("Debitoren/Kreditoren"), "5",
                LocalDateTime.now(clock).format(CREATED), "", q("RE"), q("Rechnio"), q(""),
                num(org.getDatevBeraterNr()), num(org.getDatevMandantNr()),
                LocalDate.of(today.getYear(), 1, 1).format(YMD), "4",
                "", "", q(""), q(""), "", "", "", q(""),
                "", q(""), "", "", q(skr), "", "", q(""), q("")));
        return String.join(";", f);
    }

    private static String debitor(Customer c) {
        Row r = new Row(DatevAccounts.DEBITOR_BASE + c.getCustomerNumber(), c.getName());
        r.vatId(c.getUstId());
        r.address(c.getStreet(), c.getZip(), c.getCity(), c.getCountry());
        r.set(EMAIL, c.getEmail(), 60);
        r.set(KUNDENNUMMER, String.valueOf(c.getCustomerNumber()), 15);
        return r.toString();
    }

    private static String kreditor(Supplier s, IncomingInvoice last) {
        Row r = new Row(DatevAccounts.KREDITOR_BASE + s.getSupplierNumber(), s.getName());
        r.vatId(s.getVatId());
        r.set(STEUERNUMMER, s.getTaxId(), 20);
        r.set(KUNDENNUMMER, String.valueOf(s.getSupplierNumber()), 15);
        if (last != null) {
            r.address(last.getSupplierStreet(), last.getSupplierZip(), last.getSupplierCity(), last.getSupplierCountry());
            String iban = last.getSupplierIban() == null ? null : last.getSupplierIban().replace(" ", "").toUpperCase();
            if (iban != null && !iban.isBlank()) {
                r.set(IBAN, iban, 34);
                r.raw(HAUPTBANK, "1");
            }
        }
        return r.toString();
    }

    /** Eine Datenzeile; alle Felder sind Text in Anfuehrungszeichen, ausser Konto und Kennzeichen. */
    private static final class Row {
        private final String[] fields = new String[COLUMNS.size()];

        Row(int account, String name) {
            Arrays.fill(fields, q(""));
            raw(KONTO, String.valueOf(account));
            // Rechnio unterscheidet nicht zwischen Unternehmen und Privatperson: Adressatentyp 0 = keine Angabe
            set(NAME_KEINE_ANGABE, name, 50);
            raw(ADRESSATENTYP, "0");
            set(KURZBEZEICHNUNG, name, 15);
        }

        void vatId(String ustId) {
            if (ustId == null) return;
            String v = ustId.replace(" ", "").toUpperCase();
            if (v.length() > 2 && Character.isLetter(v.charAt(0)) && Character.isLetter(v.charAt(1))) {
                set(EU_LAND, v.substring(0, 2), 2);
                set(EU_USTID, v.substring(2), 13);
            }
        }

        void address(String street, String zip, String city, String country) {
            if (street == null && zip == null && city == null) return;
            set(ADRESSART, "STR", 3);
            set(STRASSE, street, 36);
            set(PLZ, zip, 10);
            set(ORT, city, 30);
            set(LAND, country == null || country.isBlank() ? "DE" : country, 2);
        }

        void set(int field, String value, int maxLength) {
            if (value == null || value.isBlank()) return;
            String v = value.strip();
            fields[field - 1] = q(v.length() <= maxLength ? v : v.substring(0, maxLength));
        }

        void raw(int field, String value) {
            fields[field - 1] = value;
        }

        @Override
        public String toString() {
            return String.join(";", fields);
        }
    }

    private static List<String> columns() {
        try (InputStream in = DatevStammdatenServiceImpl.class.getResourceAsStream("/datev/debitoren-kreditoren-felder.txt")) {
            List<String> names = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .toList();
            if (names.size() != 254) {
                throw new IllegalStateException("DATEV Debitoren/Kreditoren: 254 Felder erwartet, gefunden " + names.size());
            }
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String num(Integer v) {
        return v == null ? "" : v.toString();
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }
}
