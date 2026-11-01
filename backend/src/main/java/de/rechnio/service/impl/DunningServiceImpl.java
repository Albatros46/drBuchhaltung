package de.rechnio.service.impl;

import static de.rechnio.util.GermanFormat.date;
import static de.rechnio.util.GermanFormat.money;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.DunningMailRequest;
import de.rechnio.dto.DunningResponse;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.dto.OutgoingMail;
import de.rechnio.dto.PdfDocument;
import de.rechnio.entity.AuditLog;
import de.rechnio.entity.Customer;
import de.rechnio.entity.Dunning;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.Organization;
import de.rechnio.enums.DocType;
import de.rechnio.enums.DunningLevel;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.AuditLogRepository;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.DunningRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.DunningService;
import de.rechnio.service.MailService;
import de.rechnio.util.Letterhead;
import de.rechnio.util.PdfRenderer;

@Service
public class DunningServiceImpl implements DunningService {

    private final DunningRepository dunnings;
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final TemplateEngine pdfTemplateEngine;
    private final MailService mailService;
    private final AuditLogRepository audit;
    private final Clock clock;

    public DunningServiceImpl(DunningRepository dunnings, InvoiceRepository invoices, CustomerRepository customers,
                              OrganizationRepository organizations, TemplateEngine pdfTemplateEngine,
                              MailService mailService, AuditLogRepository audit, Clock clock) {
        this.dunnings = dunnings;
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.pdfTemplateEngine = pdfTemplateEngine;
        this.mailService = mailService;
        this.audit = audit;
        this.clock = clock;
    }

    /** Werte fuer templates/pdf/dunning.xhtml, bereits deutsch formatiert. */
    public record View(String title, String issueDate, String deadline, String invoiceNumber, String invoiceDate,
                       String invoiceDueDate, int customerNumber, String openAmount, List<FeeRow> fees,
                       String totalDue, String text, String paymentText, boolean finalNotice, Letterhead letter) {
    }

    public record FeeRow(String label, String amount) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<DunningResponse> list(CurrentUser user, UUID invoiceId) {
        Invoice inv = invoice(user, invoiceId);
        List<Dunning> all = history(user, inv.getId());
        List<DunningResponse> result = new ArrayList<>();
        for (Dunning d : all) result.add(response(d, all));
        return result;
    }

    @Override
    @Transactional
    public DunningResponse create(CurrentUser user, UUID invoiceId) {
        Invoice inv = invoice(user, invoiceId);
        LocalDate today = LocalDate.now(clock);
        if (inv.getDocType() != DocType.RECHNUNG || inv.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.conflict("Gemahnt werden können nur offene Rechnungen");
        }
        if (inv.getDueDate() == null || !today.isAfter(inv.getDueDate())) {
            throw ApiException.conflict("Die Rechnung ist noch nicht überfällig"
                    + (inv.getDueDate() == null ? "" : " (fällig am " + date(inv.getDueDate()) + ")"));
        }
        List<Dunning> history = history(user, inv.getId());
        DunningLevel level = DunningLevel.ZAHLUNGSERINNERUNG;
        if (!history.isEmpty()) {
            Dunning last = history.getLast();
            level = last.getLevel().next();
            if (level == null) {
                throw ApiException.conflict("Die höchste Mahnstufe ist erreicht. Weitere Schritte "
                        + "(z. B. gerichtliches Mahnverfahren) bitte außerhalb der App veranlassen.");
            }
            if (!today.isAfter(last.getDeadline())) {
                throw ApiException.conflict("Die Frist der " + last.getLevel().title() + " läuft noch bis "
                        + date(last.getDeadline()));
            }
        }
        Organization org = organization(user);
        BigDecimal fee = level.withFee() && org.getDunningFee() != null ? org.getDunningFee() : BigDecimal.ZERO;
        Dunning d = dunnings.save(new Dunning(user.organizationId(), inv.getId(), level, today,
                inv.getGrossTotal(), fee.setScale(2)));
        audit.save(new AuditLog(user.organizationId(), "INVOICE", inv.getId(), "DUNNING",
                level.title() + " bis " + date(d.getDeadline()), user.email()));
        history.add(d);
        return response(d, history);
    }

    @Override
    @Transactional(readOnly = true)
    public PdfDocument pdf(CurrentUser user, UUID dunningId) {
        Dunning d = dunning(user, dunningId);
        Invoice inv = invoice(user, d.getInvoiceId());
        Organization org = organization(user);
        Customer customer = customer(user, inv);
        List<Dunning> history = history(user, inv.getId());

        Context ctx = new Context(Locale.GERMANY);
        ctx.setVariable("v", view(d, history, inv, org, customer));
        String xhtml = pdfTemplateEngine.process("dunning", ctx);
        return new PdfDocument(fileName(d, inv), PdfRenderer.toPdfA(xhtml));
    }

    @Override
    @Transactional(readOnly = true)
    public DunningMailRequest draft(CurrentUser user, UUID dunningId) {
        Dunning d = dunning(user, dunningId);
        Invoice inv = invoice(user, d.getInvoiceId());
        Organization org = organization(user);
        Customer customer = customer(user, inv);
        DunningResponse r = response(d, history(user, inv.getId()));

        String body = "Guten Tag,\n\n" + intro(d.getLevel(), inv) + "\n\n"
                + "Offener Betrag inklusive Mahngebühren: " + money(r.totalDue()) + "\n"
                + "Bitte überweisen Sie bis zum " + date(d.getDeadline())
                + InvoiceMailServiceImpl.paymentAccount(org)
                + "Verwendungszweck: " + inv.getNumber() + "\n\n"
                + "Sollten Sie die Zahlung inzwischen veranlasst haben, betrachten Sie diese Nachricht bitte als gegenstandslos.\n"
                + InvoiceMailServiceImpl.signature(org);
        return new DunningMailRequest(customer.getEmail() == null ? "" : customer.getEmail(),
                d.getLevel().title() + " zu Rechnung " + inv.getNumber() + " – " + org.getName(), body);
    }

    @Override
    @Transactional
    public DunningResponse send(CurrentUser user, UUID dunningId, DunningMailRequest req) {
        Dunning d = dunning(user, dunningId);
        Organization org = organization(user);
        mailService.normalizeRecipients(req.to());
        PdfDocument pdf = pdf(user, dunningId);
        String sentTo = mailService.send(new OutgoingMail(org.getName(), org.getEmail(), req.to(), req.subject(),
                req.message(), new GeneratedFile(pdf.filename(), "application/pdf", pdf.content())));
        d.markSent(sentTo, Instant.now(clock));
        audit.save(new AuditLog(user.organizationId(), "INVOICE", d.getInvoiceId(), "DUNNING_SENT",
                d.getLevel().title() + " an " + sentTo, user.email()));
        return response(d, history(user, d.getInvoiceId()));
    }

    // --- Inhalt ---

    static View view(Dunning d, List<Dunning> history, Invoice inv, Organization org, Customer customer) {
        List<FeeRow> fees = new ArrayList<>();
        for (Dunning h : upTo(d, history)) {
            if (h.getFee().signum() > 0) {
                fees.add(new FeeRow("Mahngebühr " + h.getLevel().title() + " vom " + date(h.getIssueDate()),
                        money(h.getFee())));
            }
        }
        BigDecimal totalDue = d.getOpenAmount().add(totalFees(d, history));
        String paymentText = "Bitte überweisen Sie den Gesamtbetrag von " + money(totalDue) + " bis zum "
                + date(d.getDeadline()) + (org.getIban() == null ? "" : " auf das unten angegebene Konto")
                + " unter Angabe der Rechnungsnummer " + inv.getNumber() + ".";
        return new View(d.getLevel().title(), date(d.getIssueDate()), date(d.getDeadline()), inv.getNumber(),
                date(inv.getIssueDate()), date(inv.getDueDate()), customer.getCustomerNumber(),
                money(d.getOpenAmount()), fees, money(totalDue), intro(d.getLevel(), inv), paymentText,
                d.getLevel().next() == null, Letterhead.of(org, customer));
    }

    private static String intro(DunningLevel level, Invoice inv) {
        String ref = "unsere Rechnung " + inv.getNumber() + " vom " + date(inv.getIssueDate()) + " über "
                + money(inv.getGrossTotal());
        return switch (level) {
            case ZAHLUNGSERINNERUNG -> "sicher ist es im Alltag untergegangen: " + capitalizeRef(ref)
                    + " war am " + date(inv.getDueDate()) + " fällig. Bis heute konnten wir keinen Zahlungseingang feststellen.";
            case MAHNUNG_1 -> "leider ist " + ref + " trotz unserer Zahlungserinnerung noch nicht beglichen.";
            case MAHNUNG_2 -> "trotz unserer bisherigen Erinnerungen ist " + ref + " weiterhin offen. "
                    + "Sollte der Betrag nicht fristgerecht eingehen, behalten wir uns weitere Schritte vor.";
        };
    }

    private static String capitalizeRef(String ref) {
        return Character.toUpperCase(ref.charAt(0)) + ref.substring(1);
    }

    static DunningResponse response(Dunning d, List<Dunning> history) {
        BigDecimal totalFees = totalFees(d, history);
        return new DunningResponse(d.getId(), d.getInvoiceId(), d.getLevel(), d.getLevel().title(), d.getIssueDate(),
                d.getDeadline(), d.getOpenAmount(), d.getFee(), totalFees, d.getOpenAmount().add(totalFees),
                d.getSentAt(), d.getSentTo());
    }

    /** Gebuehren aller Stufen bis einschliesslich d. */
    private static BigDecimal totalFees(Dunning d, List<Dunning> history) {
        return upTo(d, history).stream().map(Dunning::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Dunning> upTo(Dunning d, List<Dunning> history) {
        return history.stream().filter(h -> h.getLevel().ordinal() <= d.getLevel().ordinal()).toList();
    }

    private static String fileName(Dunning d, Invoice inv) {
        // "1. Mahnung" -> "1-Mahnung-RE-2026-0001.pdf"
        String title = d.getLevel().title().replace(". ", "-");
        return (title + "-" + inv.getNumber()).replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    // --- Laden ---

    private List<Dunning> history(CurrentUser user, UUID invoiceId) {
        List<Dunning> list = new ArrayList<>(dunnings.findByInvoiceIdAndOrganizationIdOrderByCreatedAtAsc(
                invoiceId, user.organizationId()));
        list.sort(Comparator.comparing(Dunning::getLevel));
        return list;
    }

    private Dunning dunning(CurrentUser user, UUID id) {
        return dunnings.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Mahnung"));
    }

    private Invoice invoice(CurrentUser user, UUID id) {
        return invoices.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Rechnung"));
    }

    private Organization organization(CurrentUser user) {
        return organizations.findById(user.organizationId()).orElseThrow(() -> ApiException.notFound("Firma"));
    }

    private Customer customer(CurrentUser user, Invoice inv) {
        return customers.findByIdAndOrganizationId(inv.getCustomerId(), user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Kunde"));
    }
}
