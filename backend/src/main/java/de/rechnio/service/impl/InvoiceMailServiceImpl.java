package de.rechnio.service.impl;

import static de.rechnio.util.GermanFormat.date;
import static de.rechnio.util.GermanFormat.money;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.dto.InvoiceMailRequest;
import de.rechnio.dto.InvoiceResponse;
import de.rechnio.dto.OutgoingMail;
import de.rechnio.dto.PdfDocument;
import de.rechnio.entity.AuditLog;
import de.rechnio.entity.Customer;
import de.rechnio.entity.Invoice;
import de.rechnio.entity.Organization;
import de.rechnio.enums.DocType;
import de.rechnio.enums.InvoiceStatus;
import de.rechnio.enums.MailAttachment;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.AuditLogRepository;
import de.rechnio.repository.CustomerRepository;
import de.rechnio.repository.InvoiceRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.ERechnungService;
import de.rechnio.service.InvoiceMailService;
import de.rechnio.service.InvoicePdfService;
import de.rechnio.service.MailService;

@Service
public class InvoiceMailServiceImpl implements InvoiceMailService {

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final InvoicePdfService pdfService;
    private final ERechnungService eRechnungService;
    private final MailService mailService;
    private final AuditLogRepository audit;
    private final Clock clock;

    public InvoiceMailServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                                  OrganizationRepository organizations, InvoicePdfService pdfService,
                                  ERechnungService eRechnungService, MailService mailService,
                                  AuditLogRepository audit, Clock clock) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.pdfService = pdfService;
        this.eRechnungService = eRechnungService;
        this.mailService = mailService;
        this.audit = audit;
        this.clock = clock;
    }

    private record Context(Invoice invoice, Organization org, Customer customer, Invoice cancelled) {
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceMailRequest draft(CurrentUser user, UUID invoiceId) {
        Context c = load(user, invoiceId);
        Invoice inv = c.invoice();
        MailAttachment attachment = inv.getDocType() == DocType.ANGEBOT ? MailAttachment.PDF
                : !blank(c.customer().getLeitwegId()) ? MailAttachment.XRECHNUNG
                : MailAttachment.ZUGFERD;
        String to = c.customer().getEmail() == null ? "" : c.customer().getEmail();
        return new InvoiceMailRequest(to, subject(c), body(c), attachment);
    }

    @Override
    @Transactional
    public InvoiceResponse send(CurrentUser user, UUID invoiceId, InvoiceMailRequest req) {
        Context c = load(user, invoiceId);
        Invoice inv = c.invoice();
        if (inv.getDocType() == DocType.ANGEBOT && req.attachment() != MailAttachment.PDF) {
            throw ApiException.badRequest("Angebote werden als PDF verschickt");
        }
        mailService.normalizeRecipients(req.to());

        // Anhang zuerst erzeugen: fehlen Angaben fuer die E-Rechnung, wird nichts verschickt (HTTP 422)
        GeneratedFile file = attachment(user, inv, req.attachment());
        String sentTo = mailService.send(new OutgoingMail(c.org().getName(), c.org().getEmail(), req.to(),
                req.subject(), req.message(), file));

        inv.markSent(sentTo, Instant.now(clock));
        audit.save(new AuditLog(user.organizationId(), "INVOICE", inv.getId(), "SENT",
                req.attachment() + " an " + sentTo, user.email()));
        return InvoiceResponse.from(inv);
    }

    private GeneratedFile attachment(CurrentUser user, Invoice inv, MailAttachment kind) {
        return switch (kind) {
            case ZUGFERD -> eRechnungService.zugferd(user, inv.getId());
            case XRECHNUNG -> eRechnungService.xrechnung(user, inv.getId());
            case PDF -> {
                PdfDocument pdf = pdfService.render(user, inv.getId());
                yield new GeneratedFile(pdf.filename(), "application/pdf", pdf.content());
            }
        };
    }

    private Context load(CurrentUser user, UUID invoiceId) {
        Invoice inv = invoices.findByIdAndOrganizationId(invoiceId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Beleg"));
        if (inv.getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.conflict("Entwürfe können nicht verschickt werden. Bitte zuerst ausstellen.");
        }
        Organization org = organizations.findById(user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Firma"));
        Customer customer = customers.findByIdAndOrganizationId(inv.getCustomerId(), user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Kunde"));
        Invoice cancelled = inv.getCancelsInvoiceId() == null ? null
                : invoices.findByIdAndOrganizationId(inv.getCancelsInvoiceId(), user.organizationId()).orElse(null);
        return new Context(inv, org, customer, cancelled);
    }

    private static String subject(Context c) {
        Invoice inv = c.invoice();
        String doc = switch (inv.getDocType()) {
            case RECHNUNG -> "Rechnung";
            case ANGEBOT -> "Angebot";
            case STORNO -> "Stornorechnung";
        };
        return doc + " " + inv.getNumber() + " von " + c.org().getName();
    }

    private static String body(Context c) {
        Invoice inv = c.invoice();
        Organization org = c.org();
        StringBuilder sb = new StringBuilder("Guten Tag,\n\n");
        switch (inv.getDocType()) {
            case ANGEBOT -> sb.append("anbei erhalten Sie unser Angebot ").append(inv.getNumber())
                    .append(" über ").append(money(inv.getGrossTotal())).append(".\n")
                    .append("Bei Fragen melden Sie sich gerne.\n");
            case STORNO -> sb.append("anbei erhalten Sie die Stornorechnung ").append(inv.getNumber())
                    .append(c.cancelled() == null ? "" : " zu unserer Rechnung " + c.cancelled().getNumber())
                    .append(" über ").append(money(inv.getGrossTotal().abs())).append(".\n");
            case RECHNUNG -> {
                sb.append("anbei erhalten Sie unsere Rechnung ").append(inv.getNumber());
                if (inv.getIssueDate() != null) sb.append(" vom ").append(date(inv.getIssueDate()));
                sb.append(" über ").append(money(inv.getGrossTotal())).append(".\n");
                if (inv.getDueDate() != null) {
                    sb.append("Bitte überweisen Sie den Betrag bis zum ").append(date(inv.getDueDate()));
                    sb.append(paymentAccount(org)).append("Verwendungszweck: ").append(inv.getNumber()).append("\n");
                }
            }
        }
        return sb.append(signature(org)).toString();
    }

    /** " auf folgendes Konto:\nIBAN …, BIC …\n" oder ".\n" ohne IBAN. */
    static String paymentAccount(Organization org) {
        if (blank(org.getIban())) return ".\n";
        return " auf folgendes Konto:\nIBAN " + org.getIban() + (blank(org.getBic()) ? "" : ", BIC " + org.getBic()) + "\n";
    }

    /** Grussformel mit Ansprechpartner, Firma und Telefon. */
    static String signature(Organization org) {
        StringBuilder sb = new StringBuilder("\nMit freundlichen Grüßen\n");
        if (!blank(org.getContactName())) sb.append(org.getContactName()).append("\n");
        sb.append(org.getName());
        if (!blank(org.getPhone())) sb.append("\nTel. ").append(org.getPhone());
        return sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
