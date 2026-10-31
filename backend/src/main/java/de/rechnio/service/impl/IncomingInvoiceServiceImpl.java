package de.rechnio.service.impl;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.mustangproject.BankDetails;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlement;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.GeneratedFile;
import de.rechnio.dto.IncomingInvoiceResponse;
import de.rechnio.entity.AuditLog;
import de.rechnio.entity.IncomingInvoice;
import de.rechnio.entity.IncomingInvoiceFile;
import de.rechnio.enums.EInvoiceFormat;
import de.rechnio.enums.IncomingDocType;
import de.rechnio.enums.IncomingStatus;
import de.rechnio.enums.ValidationStatus;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.AuditLogRepository;
import de.rechnio.repository.IncomingInvoiceFileRepository;
import de.rechnio.repository.IncomingInvoiceRepository;
import de.rechnio.service.IncomingInvoiceService;

@Service
public class IncomingInvoiceServiceImpl implements IncomingInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(IncomingInvoiceServiceImpl.class);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Pattern ROOT = Pattern.compile("<(?:[A-Za-z0-9_-]+:)?(CrossIndustryInvoice|Invoice|CreditNote)[\\s>]");
    private static final Pattern REPORT_ERROR = Pattern.compile("<error[^>]*>([^<]+)</error>");
    private static final int MAX_MESSAGES = 10;

    private final IncomingInvoiceRepository invoices;
    private final IncomingInvoiceFileRepository files;
    private final AuditLogRepository audit;
    private final Clock clock;

    public IncomingInvoiceServiceImpl(IncomingInvoiceRepository invoices, IncomingInvoiceFileRepository files,
                                      AuditLogRepository audit, Clock clock) {
        this.invoices = invoices;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IncomingInvoiceResponse receive(CurrentUser user, String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("Die Datei ist leer");
        }
        String sha256 = sha256(content);
        invoices.findByOrganizationIdAndSha256(user.organizationId(), sha256).ifPresent(existing -> {
            throw ApiException.conflict("Diese Datei wurde bereits eingelesen (" + label(existing) + ")");
        });

        boolean pdf = isPdf(content);
        EInvoiceFormat format = pdf ? EInvoiceFormat.ZUGFERD : xmlFormat(content);
        org.mustangproject.Invoice parsed = parse(content, pdf);

        IncomingInvoice inv = new IncomingInvoice(user.organizationId(), format,
                filename == null || filename.isBlank() ? (pdf ? "rechnung.pdf" : "rechnung.xml") : filename, sha256);
        fill(inv, parsed);
        if (inv.getSupplierName() == null && (format == EInvoiceFormat.UBL || format == EInvoiceFormat.XRECHNUNG_UBL)) {
            inv.setSupplierName(ublSupplierName(content));
        }
        validate(inv, content);
        invoices.save(inv);
        files.save(new IncomingInvoiceFile(inv.getId(), pdf ? "application/pdf" : "application/xml", content));
        audit.save(new AuditLog(user.organizationId(), "INCOMING_INVOICE", inv.getId(), "RECEIVED",
                format + " " + label(inv), user.email()));
        return IncomingInvoiceResponse.from(inv);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingInvoiceResponse> list(CurrentUser user) {
        return invoices.findByOrganizationIdOrderByReceivedAtDesc(user.organizationId())
                .stream().map(IncomingInvoiceResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IncomingInvoiceResponse get(CurrentUser user, UUID id) {
        return IncomingInvoiceResponse.from(load(user, id));
    }

    @Override
    @Transactional
    public IncomingInvoiceResponse markPaid(CurrentUser user, UUID id, LocalDate paidAt) {
        IncomingInvoice inv = load(user, id);
        if (inv.getStatus() == IncomingStatus.PAID) {
            throw ApiException.conflict("Diese Rechnung ist bereits als bezahlt markiert");
        }
        inv.setStatus(IncomingStatus.PAID);
        inv.setPaidAt(paidAt == null ? LocalDate.now(clock) : paidAt);
        audit.save(new AuditLog(user.organizationId(), "INCOMING_INVOICE", inv.getId(), "PAID",
                inv.getPaidAt().toString(), user.email()));
        return IncomingInvoiceResponse.from(inv);
    }

    @Override
    @Transactional(readOnly = true)
    public GeneratedFile original(CurrentUser user, UUID id) {
        IncomingInvoice inv = load(user, id);
        IncomingInvoiceFile file = files.findById(inv.getId())
                .orElseThrow(() -> ApiException.notFound("Originaldatei"));
        return new GeneratedFile(inv.getOriginalFilename(), file.getContentType(), file.getContent());
    }

    // --- Einlesen ---

    static boolean isPdf(byte[] content) {
        return content.length >= 5 && new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
    }

    static EInvoiceFormat xmlFormat(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 64 * 1024), StandardCharsets.UTF_8);
        Matcher root = ROOT.matcher(head);
        if (!root.find()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Keine E-Rechnung: erwartet wird ein ZUGFeRD-PDF oder XML (XRechnung, CII oder UBL)");
        }
        boolean xrechnung = head.contains("urn:xeinkauf.de:kosit:xrechnung");
        boolean cii = root.group(1).equals("CrossIndustryInvoice");
        if (cii) return xrechnung ? EInvoiceFormat.XRECHNUNG_CII : EInvoiceFormat.CII;
        return xrechnung ? EInvoiceFormat.XRECHNUNG_UBL : EInvoiceFormat.UBL;
    }

    private static org.mustangproject.Invoice parse(byte[] content, boolean pdf) {
        try {
            ZUGFeRDInvoiceImporter importer;
            if (pdf) {
                importer = new ZUGFeRDInvoiceImporter(new ByteArrayInputStream(content));
                if (!importer.canParse()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Das PDF enthält keine E-Rechnung (kein eingebettetes ZUGFeRD/Factur-X-XML)");
                }
            } else {
                importer = new ZUGFeRDInvoiceImporter();
                importer.setRawXML(content);
            }
            // Abweichende Rundung im Lieferantensystem soll das Einlesen nicht verhindern;
            // die Pruefung unten meldet echte Rechenfehler.
            importer.doIgnoreCalculationErrors();
            return importer.extractInvoice();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.info("E-Rechnung konnte nicht gelesen werden", e);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Die Rechnungsdaten konnten nicht gelesen werden: " + e.getMessage());
        }
    }

    private static void fill(IncomingInvoice inv, org.mustangproject.Invoice src) {
        inv.setDocumentType("381".equals(src.getDocumentCode()) ? IncomingDocType.GUTSCHRIFT : IncomingDocType.RECHNUNG);
        inv.setNumber(src.getNumber());
        inv.setIssueDate(date(src.getIssueDate()));
        inv.setDueDate(date(src.getDueDate()));
        inv.setCurrency(src.getCurrency() == null ? "EUR" : src.getCurrency());
        inv.setBuyerReference(src.getReferenceNumber());
        inv.setPaymentTerms(src.getPaymentTermDescription());

        TradeParty seller = src.getSender() instanceof TradeParty tp ? tp : null;
        if (seller != null) {
            inv.supplier(seller.getName(), seller.getStreet(), seller.getZIP(), seller.getLocation(),
                    seller.getCountry(), seller.getVATID(), seller.getTaxID(), iban(src, seller));
        }

        TransactionCalculator calc = new TransactionCalculator(src);
        BigDecimal gross = scale(calc.getGrandTotal());
        BigDecimal net = scale(calc.getTaxBasis());
        inv.totals(net, gross.subtract(net), gross);

        IZUGFeRDExportableItem[] items = src.getZFItems();
        if (items != null) {
            for (IZUGFeRDExportableItem it : items) {
                if (!(it instanceof Item item)) continue;
                Product p = item.getProduct();
                BigDecimal lineNet = item.getLineTotalAmount() != null ? item.getLineTotalAmount()
                        : lineNet(item.getPrice(), item.getQuantity(), item.getBasisQuantity());
                inv.addLine(p == null ? null : p.getName(), item.getQuantity(), p == null ? null : p.getUnit(),
                        item.getPrice(), p == null ? null : p.getVATPercent(), scale(lineNet));
            }
        }
    }

    /**
     * UBL erlaubt den Namen nur in PartyLegalEntity/RegistrationName (PartyName ist optional);
     * Mustang liest nur PartyName. Ohne DOCTYPE geparst, damit keine externen Entitaeten geladen werden (XXE).
     */
    static String ublSupplierName(byte[] content) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            XPath xp = XPathFactory.newInstance().newXPath();
            String party = "//*[local-name()='AccountingSupplierParty']/*[local-name()='Party']";
            for (String path : List.of(party + "/*[local-name()='PartyName']/*[local-name()='Name']",
                    party + "/*[local-name()='PartyLegalEntity']/*[local-name()='RegistrationName']")) {
                String name = xp.evaluate(path, doc).trim();
                if (!name.isEmpty()) return name;
            }
        } catch (Exception e) {
            log.info("Lieferantenname aus UBL nicht lesbar", e);
        }
        return null;
    }

    /** IBAN steht je nach Format beim Verkaeufer oder in den Zahlungsanweisungen. */
    private static String iban(org.mustangproject.Invoice src, TradeParty seller) {
        if (seller.getBankDetails() != null) {
            for (BankDetails b : seller.getBankDetails()) {
                if (b.getIBAN() != null) return b.getIBAN();
            }
        }
        IZUGFeRDTradeSettlement[] settlements = src.getTradeSettlement();
        if (settlements != null) {
            for (IZUGFeRDTradeSettlement s : settlements) {
                if (s instanceof BankDetails b && b.getIBAN() != null) return b.getIBAN();
            }
        }
        return null;
    }

    /** Prueft gegen XSD, EN 16931 / XRechnung-Schematron und bei PDF zusaetzlich PDF/A. */
    private static void validate(IncomingInvoice inv, byte[] content) {
        try {
            ZUGFeRDValidator validator = new ZUGFeRDValidator();
            String report = validator.validate(content, inv.getOriginalFilename());
            if (validator.wasCompletelyValid()) {
                inv.validation(ValidationStatus.VALID, null);
                return;
            }
            Set<String> messages = new LinkedHashSet<>();
            Matcher m = REPORT_ERROR.matcher(report);
            while (m.find() && messages.size() < MAX_MESSAGES) {
                messages.add(m.group(1).replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
                        .replace("&gt;", ">").replace("&amp;", "&")
                        .replaceAll("\\s*\\[ID [^]]+\\] from \\S+?\\)?$", "") // technischer Verweis auf das XSLT
                        .trim());
            }
            if (messages.isEmpty()) messages.add("Die Prüfung hat Fehler gemeldet, aber keine Einzelheiten geliefert.");
            String joined = String.join("\n", messages);
            inv.validation(ValidationStatus.INVALID, joined.length() > 4000 ? joined.substring(0, 4000) : joined);
        } catch (Exception e) {
            log.warn("Validierung fehlgeschlagen", e);
            inv.validation(ValidationStatus.INVALID, "Die Prüfung konnte nicht durchgeführt werden: " + e.getMessage());
        }
    }

    // --- Hilfen ---

    private IncomingInvoice load(CurrentUser user, UUID id) {
        return invoices.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Eingangsrechnung"));
    }

    private static String label(IncomingInvoice inv) {
        String supplier = inv.getSupplierName() == null ? "unbekannter Lieferant" : inv.getSupplierName();
        return inv.getNumber() == null ? supplier : inv.getNumber() + ", " + supplier;
    }

    private static BigDecimal lineNet(BigDecimal price, BigDecimal quantity, BigDecimal basis) {
        if (price == null || quantity == null) return BigDecimal.ZERO;
        BigDecimal b = basis == null || basis.signum() == 0 ? BigDecimal.ONE : basis;
        return price.multiply(quantity).divide(b, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate date(Date d) {
        return d == null ? null : d.toInstant().atZone(BERLIN).toLocalDate();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
