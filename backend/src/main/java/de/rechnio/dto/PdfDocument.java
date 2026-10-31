package de.rechnio.dto;

/** Fertig gerendertes PDF mit vorgeschlagenem Dateinamen. */
public record PdfDocument(String filename, byte[] content) {
}
