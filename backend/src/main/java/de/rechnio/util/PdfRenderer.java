package de.rechnio.util;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Rendert XHTML als PDF/A-3b: Schriften eingebettet, sRGB-Farbprofil. PDF/A-3 ist Voraussetzung,
 * damit ZUGFeRD die Rechnungsdaten als XML in dieselbe Datei einbetten kann.
 */
public final class PdfRenderer {

    /** Muss mit font-family in templates/pdf/letter.xhtml uebereinstimmen. */
    public static final String FONT_FAMILY = "Rechnio Sans";

    private PdfRenderer() {
    }

    public static byte[] toPdfA(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_B);
            builder.useColorProfile(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData());
            builder.useFont(() -> font("LiberationSans-Regular.ttf"), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> font("LiberationSans-Bold.ttf"), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF konnte nicht erzeugt werden", e);
        }
    }

    private static InputStream font(String file) {
        InputStream in = PdfRenderer.class.getResourceAsStream("/fonts/" + file);
        if (in == null) throw new IllegalStateException("Schrift fehlt im Classpath: " + file);
        return in;
    }
}
