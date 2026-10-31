package de.rechnio.dto;

/**
 * Zu versendende E-Mail mit genau einem Anhang.
 *
 * @param senderName Anzeigename des Absenders (Firmenname)
 * @param replyTo    Antwortadresse (Firmen-E-Mail) oder null
 * @param to         Empfaenger, durch Komma getrennt
 */
public record OutgoingMail(String senderName, String replyTo, String to, String subject, String text,
                           GeneratedFile attachment) {
}
