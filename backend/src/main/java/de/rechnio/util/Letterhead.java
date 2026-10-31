package de.rechnio.util;

import static de.rechnio.util.GermanFormat.join;

import java.util.ArrayList;
import java.util.List;

import de.rechnio.entity.Customer;
import de.rechnio.entity.Organization;

/**
 * Briefkopf nach DIN 5008 fuer alle Belege (Rechnung, Mahnung):
 * Absender, Anschriftfeld des Empfaengers und Fusszeile mit Steuer- und Bankangaben.
 * Wird in templates/pdf/letter.xhtml als Variable "letter" verwendet.
 */
public record Letterhead(String senderName, String senderLine, Party recipient, List<String> footer) {

    public record Party(String name, String street, String zipCity, String ustId) {
    }

    public static Letterhead of(Organization org, Customer customer) {
        String senderLine = join(" · ", org.getName(), org.getStreet(), join(" ", org.getZip(), org.getCity()));
        Party recipient = new Party(customer.getName(), customer.getStreet(),
                join(" ", customer.getZip(), customer.getCity()), customer.getUstId());
        return new Letterhead(org.getName(), senderLine, recipient, footer(org, senderLine));
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
