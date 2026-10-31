package de.rechnio.enums;

/** Welche Datei an die Rechnungs-E-Mail angehaengt wird. */
public enum MailAttachment {
    /** ZUGFeRD: PDF mit eingebetteten Rechnungsdaten (Standard fuer Geschaeftskunden). */
    ZUGFERD,
    /** Nur das PDF (z. B. Angebote oder Privatkunden). */
    PDF,
    /** XRechnung-XML (oeffentliche Auftraggeber). */
    XRECHNUNG
}
