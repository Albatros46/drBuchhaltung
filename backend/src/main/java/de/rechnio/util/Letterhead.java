package de.rechnio.util;

import static de.rechnio.util.GermanFormat.join;

import java.util.ArrayList;
import java.util.List;

import de.rechnio.entity.Customer;
import de.rechnio.entity.Organization;
import de.rechnio.entity.OrganizationLogo;

/**
 * Briefkopf nach DIN 5008 fuer alle Belege (Rechnung, Mahnung):
 * Absender, Anschriftfeld des Empfaengers und Fusszeile mit Steuer- und Bankangaben.
 * Wird in templates/pdf/letter.xhtml als Variable "letter" verwendet.
 */
public record Letterhead(String senderName, String senderLine, Party recipient, List<String> footer,
                         String logoDataUri, String logoStyle) {

    /** Platz fuer das Logo im Briefkopf (oben rechts). */
    private static final double LOGO_MAX_WIDTH_MM = 60;
    private static final double LOGO_MAX_HEIGHT_MM = 20;

    public record Party(String name, String street, String zipCity, String ustId) {
    }

    /**
     * Groesse in mm, die in 60 x 20 mm passt und das Seitenverhaeltnis behaelt. Explizite Masse sind noetig,
     * weil der PDF-Renderer ein per max-width verkleinertes Bild sonst links im Kasten platziert.
     */
    static String logoStyle(int widthPx, int heightPx) {
        double ratio = (double) widthPx / heightPx;
        double w = LOGO_MAX_WIDTH_MM;
        double h = w / ratio;
        if (h > LOGO_MAX_HEIGHT_MM) {
            h = LOGO_MAX_HEIGHT_MM;
            w = h * ratio;
        }
        return String.format(java.util.Locale.ROOT, "width: %.1fmm; height: %.1fmm;", w, h);
    }

    /**
     * @param logo Firmenlogo oder null; erscheint rechts im Briefkopf
     */
    public static Letterhead of(Organization org, Customer customer, OrganizationLogo logo) {
        String senderLine = join(" · ", org.getName(), org.getStreet(), join(" ", org.getZip(), org.getCity()));
        Party recipient = new Party(customer.getName(), customer.getStreet(),
                join(" ", customer.getZip(), customer.getCity()), customer.getUstId());
        return new Letterhead(org.getName(), senderLine, recipient, footer(org, senderLine),
                logo == null ? null : logo.dataUri(),
                logo == null ? null : logoStyle(logo.getWidth(), logo.getHeight()));
    }

    private static List<String> footer(Organization org, String senderLine) {
        List<String> rows = new ArrayList<>();
        rows.add(senderLine);
        rows.add(join(" · ",
                org.getSteuernummer() == null ? null : "Steuernummer " + org.getSteuernummer(),
                org.getUstId() == null ? null : "USt-IdNr. " + org.getUstId(),
                org.getPhone() == null ? null : "Tel. " + org.getPhone(),
                org.getEmail()));
        rows.add(join(" · ",
                org.getIban() == null ? null : "IBAN " + org.getIban(),
                org.getBic() == null ? null : "BIC " + org.getBic()));
        rows.removeIf(String::isEmpty);
        return rows;
    }
}
