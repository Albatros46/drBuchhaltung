package de.rechnio.dto;

/** Erzeugte Datei (z. B. XRechnung-XML oder ZUGFeRD-PDF) mit Dateiname und MIME-Typ. */
public record GeneratedFile(String filename, String contentType, byte[] content) {
}
