package de.rechnio.dto;

/**
 * Firmenlogo fuer die Anzeige in der App.
 *
 * @param dataUri data:image/...;base64,... direkt als Bildquelle verwendbar
 */
public record LogoResponse(String dataUri, int width, int height) {
}
