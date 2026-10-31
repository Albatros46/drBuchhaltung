package de.rechnio.enums;

/** Formate eingehender E-Rechnungen. */
public enum EInvoiceFormat {
    /** ZUGFeRD / Factur-X: PDF/A-3 mit eingebettetem CII-XML. */
    ZUGFERD,
    /** XRechnung in CII-Syntax. */
    XRECHNUNG_CII,
    /** XRechnung in UBL-Syntax. */
    XRECHNUNG_UBL,
    /** Sonstiges EN-16931-XML in CII-Syntax. */
    CII,
    /** Sonstiges EN-16931-XML in UBL-Syntax (z. B. Peppol). */
    UBL
}
