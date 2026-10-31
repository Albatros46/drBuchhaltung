package de.rechnio.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.PdfDocument;
import de.rechnio.entity.Customer;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.InvoiceLine;
import de.rechnio.entity.Organization;
import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.InvoicePdfService;
import de.rechnio.util.VatCalculator;

@Service
public class InvoicePdfServiceImpl implements InvoicePdfService {

    private static final Locale DE = Locale.GERMANY;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final TemplateEngine pdfTemplateEngine;

    public InvoicePdfServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                                 OrganizationRepository organizations, TemplateEngine pdfTemplateEngine) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.pdfTemplateEngine = pdfTemplateEngine;
    }

    /** Werte fuer die Vorlage, bereits deutsch formatiert. */
    public record View(String title, String numberLabel, String number, boolean draft, String issueDate, String serviceDate,
                       String dueDate, int customerNumber, String introText, String legalNote,
                       String paymentText, List<Line> lines, List<VatRow> vatRows, String net, String vat,
                       String gross, Party sender, Party recipient, String senderLine, List<String> footer) {
    }

    public record Line(int position, String description, String quantity, String unit, String unitPrice,
                       String vatRate, String net) {
    }

    public record VatRow(String label, String amount) {
    }

    public record Party(String name, String street, String zipCity, String ustId) {
    }

    @Override
    @Transactional(readOnly = true)
    public PdfDocument render(CurrentUser user, UUID invoiceId) {
        Invoice inv = invoices.findByIdAndOrganizationId(invoiceId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Beleg"));
        Organization org = organizations.findById(user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Firma"));
        Customer customer = customers.findByIdAndOrganizationId(inv.getCustomerId(), user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Kunde"));

        View view = view(inv, org, customer);
        Context ctx = new Context(DE);
        ctx.setVariable("v", view);
        String xhtml = pdfTemplateEngine.process("invoice", ctx);

        String base = view.draft() ? "Entwurf-" + view.title() : view.title() + "-" + view.number();
        return new PdfDocument(base.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf", toPdf(xhtml));
    }

    static View view(Invoice inv, Organization org, Customer customer) {
        boolean draft = inv.getStatus() == InvoiceStatus.DRAFT;
        String title = switch (inv.getDocType()) {
            case RECHNUNG -> "Rechnung";
            case ANGEBOT -> "Angebot";
            case STORNO -> "Stornorechnung";
        };
        String numberLabel = switch (inv.getDocType()) {
            case RECHNUNG -> "Rechnungs-Nr.";
            case ANGEBOT -> "Angebots-Nr.";
            case STORNO -> "Storno-Nr.";
        };

        List<Line> lines = inv.getLines().stream().map(InvoicePdfServiceImpl::line).toList();
        List<VatRow> vatRows = new ArrayList<>();
        for (VatCalculator.VatGroup g : VatCalculator.totals(inv.getLines()).groups()) {
            if (g.rate().signum() > 0) {
                vatRows.add(new VatRow("zzgl. " + plain(g.rate()) + " % USt auf " + money(g.net()), money(g.vat())));
            }
        }

        String paymentText = null;
        if (inv.getDocType() == DocType.RECHNUNG && !draft && inv.getDueDate() != null) {
            paymentText = "Bitte überweisen Sie den Betrag von " + money(inv.getGrossTotal()) + " bis zum "
                    + DATE.format(inv.getDueDate()) + " auf das unten angegebene Konto.";
        }

        Party sender = new Party(org.getName(), org.getStreet(), zipCity(org.getZip(), org.getCity()), org.getUstId());
        Party recipient = new Party(customer.getName(), customer.getStreet(),
                zipCity(customer.getZip(), customer.getCity()), customer.getUstId());

        return new View(title, numberLabel, inv.getNumber(), draft, date(inv.getIssueDate()),
                serviceDate(inv.getServiceDateFrom(), inv.getServiceDateTo()), date(inv.getDueDate()),
                customer.getCustomerNumber(), inv.getIntroText(), inv.getTaxMode().legalNote(), paymentText,
                lines, vatRows, money(inv.getNetTotal()), money(inv.getVatTotal()), money(inv.getGrossTotal()),
                sender, recipient, join(" · ", org.getName(), org.getStreet(), zipCity(org.getZip(), org.getCity())),
                footer(org));
    }

    private static Line line(InvoiceLine l) {
        return new Line(l.getPosition(), l.getDescription(), plain(l.getQuantity()), l.getUnit(),
                money(l.getUnitPrice()), plain(l.getVatRate()) + " %", money(l.getNetAmount()));
    }

    private static List<String> footer(Organization org) {
        List<String> rows = new ArrayList<>();
        rows.add(join(" · ", org.getName(), org.getStreet(), zipCity(org.getZip(), org.getCity())));
        rows.add(join(" · ",
                org.getSteuernummer() == null ? null : "Steuernummer " + org.getSteuernummer(),
                org.getUstId() == null ? null : "USt-IdNr. " + org.getUstId(),
                org.getEmail()));
        rows.add(join(" · ",
                org.getIban() == null ? null : "IBAN " + org.getIban(),
                org.getBic() == null ? null : "BIC " + org.getBic()));
        rows.removeIf(String::isEmpty);
        return rows;
    }

    private static byte[] toPdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF konnte nicht erzeugt werden", e);
        }
    }

    private static String money(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(DE).format(v);
    }

    /** Zahl ohne Waehrung, deutsche Schreibweise, ohne ueberfluessige Nachkommastellen (8 / 1,5 / 19). */
    private static String plain(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(DE);
        f.setMaximumFractionDigits(3);
        return f.format(v);
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    private static String serviceDate(LocalDate from, LocalDate to) {
        if (from == null) return null;
        if (to == null || to.equals(from)) return DATE.format(from);
        return DATE.format(from) + " – " + DATE.format(to);
    }

    private static String zipCity(String zip, String city) {
        return join(" ", zip, city);
    }

    private static String join(String sep, String... parts) {
        List<String> present = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) present.add(p);
        }
        return String.join(sep, present);
    }
}
