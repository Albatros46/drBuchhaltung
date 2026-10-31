package de.rechnio.service.impl;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.dto.InvoiceMailRequest;
import de.rechnio.dto.InvoiceResponse;
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
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class InvoiceMailServiceImpl implements InvoiceMailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMailServiceImpl.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final InvoicePdfService pdfService;
    private final ERechnungService eRechnungService;
    private final AuditLogRepository audit;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final String from;

    public InvoiceMailServiceImpl(InvoiceRepository invoices, CustomerRepository customers,
                                  OrganizationRepository organizations, InvoicePdfService pdfService,
                                  ERechnungService eRechnungService, AuditLogRepository audit,
                                  JavaMailSender mailSender, Clock clock,
                                  @Value("${rechnio.mail.from:rechnung@rechnio.local}") String from) {
        this.invoices = invoices;
        this.customers = customers;
        this.organizations = organizations;
        this.pdfService = pdfService;
        this.eRechnungService = eRechnungService;
        this.audit = audit;
        this.mailSender = mailSender;
        this.clock = clock;
        this.from = from;
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
        InternetAddress[] recipients = recipients(req.to());

        // Anhang zuerst erzeugen: fehlen Angaben fuer die E-Rechnung, wird nichts verschickt (HTTP 422)
        GeneratedFile file = attachment(user, inv, req.attachment());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(from, c.org().getName(), "UTF-8"));
            if (!blank(c.org().getEmail())) helper.setReplyTo(c.org().getEmail());
            helper.setTo(recipients);
            helper.setSubject(req.subject().trim());
            helper.setText(req.message());
            helper.addAttachment(file.filename(), new ByteArrayResource(file.content()), file.contentType());
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.warn("Versand von {} fehlgeschlagen", inv.getNumber(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "E-Mail konnte nicht gesendet werden. Ist der Mailserver erreichbar? (" + rootMessage(e) + ")");
        }

        String sentTo = Arrays.stream(recipients).map(InternetAddress::getAddress).collect(Collectors.joining(", "));
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

    static InternetAddress[] recipients(String to) {
        try {
            InternetAddress[] parsed = InternetAddress.parse(to.replace(';', ','), true);
            if (parsed.length == 0) throw new AddressException("leer");
            for (InternetAddress a : parsed) a.validate();
            return parsed;
        } catch (AddressException e) {
            throw ApiException.badRequest("Ungültige E-Mail-Adresse: " + to);
        }
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
                if (inv.getIssueDate() != null) sb.append(" vom ").append(DATE.format(inv.getIssueDate()));
                sb.append(" über ").append(money(inv.getGrossTotal())).append(".\n");
                if (inv.getDueDate() != null) {
                    sb.append("Bitte überweisen Sie den Betrag bis zum ").append(DATE.format(inv.getDueDate()));
                    sb.append(blank(org.getIban()) ? ".\n" : " auf folgendes Konto:\nIBAN " + org.getIban()
                            + (blank(org.getBic()) ? "" : ", BIC " + org.getBic()) + "\n");
                    sb.append("Verwendungszweck: ").append(inv.getNumber()).append("\n");
                }
            }
        }
        sb.append("\nMit freundlichen Grüßen\n");
        if (!blank(org.getContactName())) sb.append(org.getContactName()).append("\n");
        sb.append(org.getName());
        if (!blank(org.getPhone())) sb.append("\nTel. ").append(org.getPhone());
        return sb.toString();
    }

    private static String money(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(v);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
