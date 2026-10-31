package de.rechnio.service.impl;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import de.rechnio.dto.OutgoingMail;
import de.rechnio.exception.ApiException;
import de.rechnio.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String from;

    public MailServiceImpl(JavaMailSender mailSender,
                           @Value("${rechnio.mail.from:rechnung@rechnio.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public String normalizeRecipients(String to) {
        return Arrays.stream(parse(to)).map(InternetAddress::getAddress).collect(Collectors.joining(", "));
    }

    @Override
    public String send(OutgoingMail mail) {
        InternetAddress[] recipients = parse(mail.to());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(from, mail.senderName(), "UTF-8"));
            if (mail.replyTo() != null && !mail.replyTo().isBlank()) helper.setReplyTo(mail.replyTo());
            helper.setTo(recipients);
            helper.setSubject(mail.subject().trim());
            helper.setText(mail.text());
            helper.addAttachment(mail.attachment().filename(), new ByteArrayResource(mail.attachment().content()),
                    mail.attachment().contentType());
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.warn("E-Mail an {} fehlgeschlagen", mail.to(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "E-Mail konnte nicht gesendet werden. Ist der Mailserver erreichbar? (" + rootMessage(e) + ")");
        }
        return Arrays.stream(recipients).map(InternetAddress::getAddress).collect(Collectors.joining(", "));
    }

    private static InternetAddress[] parse(String to) {
        try {
            InternetAddress[] parsed = InternetAddress.parse(to == null ? "" : to.replace(';', ','), true);
            if (parsed.length == 0) throw new AddressException("leer");
            for (InternetAddress a : parsed) a.validate();
            return parsed;
        } catch (AddressException e) {
            throw ApiException.badRequest("Ungültige E-Mail-Adresse: " + to);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
