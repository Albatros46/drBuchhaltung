package de.rechnio.service.impl;

import static de.rechnio.util.GermanFormat.date;
import static de.rechnio.util.GermanFormat.money;
import static de.rechnio.util.GermanFormat.plain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.PdfDocument;
import de.rechnio.entity.Customer;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.InvoiceLine;
import de.rechnio.entity.Organization;
import de.rechnio.entity.OrganizationLogo;
import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationLogoRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.InvoicePdfService;
import de.rechnio.util.GermanFormat;
import de.rechnio.util.Letterhead;
import de.rechnio.util.PdfRenderer;
import de.rechnio.util.VatCalculator;

@Service
public class InvoicePdfServiceImpl implements InvoicePdfService {

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final TemplateEngine pdfTemplateEngine;
    private final OrganizationLogoRepository logos;

    public InvoicePdfServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                                 OrganizationRepository organizations, TemplateEngine pdfTemplateEngine,
                                 OrganizationLogoRepository logos) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.pdfTemplateEngine = pdfTemplateEngine;
        this.logos = logos;
    }

    /** Werte fuer die Vorlage, bereits deutsch formatiert. */
    public record View(String title, String numberLabel, String number, boolean draft, String issueDate, String serviceDate,
                       String dueDate, int customerNumber, String introText, String legalNote,
                       String paymentText, List<Line> lines, List<VatRow> vatRows, String net, String vat,
                       String gross, Letterhead letter) {
    }

    public record Line(int position, String description, String quantity, String unit, String unitPrice,
                       String vatRate, String net) {
    }

    public record VatRow(String label, String amount) {
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

        View view = view(inv, org, customer, logos.findById(org.getId()).orElse(null));
        Context ctx = new Context(Locale.GERMANY);
        ctx.setVariable("v", view);
        String xhtml = pdfTemplateEngine.process("invoice", ctx);

        String base = view.draft() ? "Entwurf-" + view.title() : view.title() + "-" + view.number();
        return new PdfDocument(base.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf", PdfRenderer.toPdfA(xhtml));
    }

    static View view(Invoice inv, Organization org, Customer customer, OrganizationLogo logo) {
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
                    + date(inv.getDueDate()) + " auf das unten angegebene Konto.";
        }

        return new View(title, numberLabel, inv.getNumber(), draft, date(inv.getIssueDate()),
                serviceDate(inv.getServiceDateFrom(), inv.getServiceDateTo()), date(inv.getDueDate()),
                customer.getCustomerNumber(), inv.getIntroText(), inv.getTaxMode().legalNote(), paymentText,
                lines, vatRows, money(inv.getNetTotal()), money(inv.getVatTotal()), money(inv.getGrossTotal()),
                Letterhead.of(org, customer, logo));
    }

    private static Line line(InvoiceLine l) {
        return new Line(l.getPosition(), l.getDescription(), plain(l.getQuantity()), l.getUnit(),
                money(l.getUnitPrice()), plain(l.getVatRate()) + " %", money(l.getNetAmount()));
    }

    private static String serviceDate(LocalDate from, LocalDate to) {
        if (from == null) return null;
        if (to == null || to.equals(from)) return date(from);
        return date(from) + " – " + GermanFormat.date(to);
    }
}
