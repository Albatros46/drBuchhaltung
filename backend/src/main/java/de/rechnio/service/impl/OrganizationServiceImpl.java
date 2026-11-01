package de.rechnio.service.impl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.LogoResponse;
import de.rechnio.dto.OrganizationDto;
import de.rechnio.entity.Organization;
import de.rechnio.entity.OrganizationLogo;
import de.rechnio.enums.Kontenrahmen;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.OrganizationLogoRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.OrganizationService;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    static final int MAX_LOGO_BYTES = 1024 * 1024;
    static final int MAX_LOGO_EDGE = 1200;
    private static final int MIN_LOGO_EDGE = 16;
    /** Schutz vor "Dekompressionsbomben": kleine Datei, riesige Pixelzahl. */
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;

    private final OrganizationRepository organizations;
    private final OrganizationLogoRepository logos;

    public OrganizationServiceImpl(OrganizationRepository organizations, OrganizationLogoRepository logos) {
        this.organizations = organizations;
        this.logos = logos;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDto get(CurrentUser user) {
        return OrganizationDto.from(load(user));
    }

    @Override
    @Transactional
    public OrganizationDto update(CurrentUser user, OrganizationDto dto) {
        Organization o = load(user);
        o.setName(dto.name());
        o.setStreet(dto.street());
        o.setZip(dto.zip());
        o.setCity(dto.city());
        o.setCountry(dto.country() == null ? "DE" : dto.country());
        o.setSteuernummer(dto.steuernummer());
        o.setUstId(dto.ustId());
        o.setKleinunternehmer(dto.kleinunternehmer());
        o.setKontenrahmen(dto.kontenrahmen() == null ? Kontenrahmen.SKR03 : dto.kontenrahmen());
        o.setIban(dto.iban());
        o.setBic(dto.bic());
        o.setEmail(dto.email());
        o.setContactName(dto.contactName());
        o.setPhone(dto.phone());
        o.setDunningFee(dto.dunningFee() == null ? BigDecimal.ZERO : dto.dunningFee());
        o.setDatevBeraterNr(dto.datevBeraterNr());
        o.setDatevMandantNr(dto.datevMandantNr());
        return OrganizationDto.from(o);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LogoResponse> logo(CurrentUser user) {
        return logos.findById(user.organizationId()).map(OrganizationServiceImpl::response);
    }

    @Override
    @Transactional
    public LogoResponse uploadLogo(CurrentUser user, byte[] content) {
        load(user);
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("Die Datei ist leer");
        }
        if (content.length > MAX_LOGO_BYTES) {
            throw ApiException.badRequest("Das Logo darf höchstens 1 MB groß sein");
        }
        Decoded decoded = decode(content);
        BufferedImage image = scaleDown(decoded.image());
        boolean png = decoded.format().equals("png");
        byte[] encoded = encode(image, png);

        OrganizationLogo logo = logos.findById(user.organizationId())
                .map(existing -> {
                    existing.replace(png ? "image/png" : "image/jpeg", encoded, image.getWidth(), image.getHeight());
                    return existing;
                })
                .orElseGet(() -> logos.save(new OrganizationLogo(user.organizationId(),
                        png ? "image/png" : "image/jpeg", encoded, image.getWidth(), image.getHeight())));
        return response(logo);
    }

    @Override
    @Transactional
    public void deleteLogo(CurrentUser user) {
        logos.deleteById(user.organizationId());
    }

    // --- Bildverarbeitung ---

    private record Decoded(BufferedImage image, String format) {
    }

    /** Erkennt das Format am Inhalt (nicht an Dateiname oder MIME-Typ) und liest nur PNG und JPEG. */
    static Decoded decode(byte[] content) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw unsupported();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (format.equals("jpg")) format = "jpeg";
                if (!format.equals("png") && !format.equals("jpeg")) throw unsupported();
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_SOURCE_PIXELS) {
                    throw ApiException.badRequest("Das Bild ist zu groß (Pixelanzahl)");
                }
                BufferedImage image = reader.read(0);
                if (image.getWidth() < MIN_LOGO_EDGE || image.getHeight() < MIN_LOGO_EDGE) {
                    throw ApiException.badRequest("Das Logo ist zu klein (mindestens 16 × 16 Pixel)");
                }
                return new Decoded(image, format);
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // z. B. CMYK-JPEG oder beschaedigte Datei
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Das Bild konnte nicht gelesen werden. Bitte als PNG oder JPEG (RGB) speichern.");
        }
    }

    private static ApiException unsupported() {
        return ApiException.badRequest("Bitte ein Logo als PNG oder JPEG hochladen");
    }

    static BufferedImage scaleDown(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = Math.min(1.0, (double) MAX_LOGO_EDGE / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * factor));
        int th = Math.max(1, (int) Math.round(h * factor));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();
        return out;
    }

    /** Neu kodieren: PNG behaelt Transparenz, JPEG wird auf weissem Grund als RGB gespeichert. */
    private static byte[] encode(BufferedImage argb, boolean png) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (png) {
                ImageIO.write(argb, "png", out);
            } else {
                BufferedImage rgb = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(argb, 0, 0, null);
                g.dispose();
                ImageIO.write(rgb, "jpeg", out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Logo konnte nicht gespeichert werden", e);
        }
    }

    private static LogoResponse response(OrganizationLogo logo) {
        return new LogoResponse(logo.dataUri(), logo.getWidth(), logo.getHeight());
    }

    private Organization load(CurrentUser user) {
        return organizations.findById(user.organizationId()).orElseThrow(() -> ApiException.notFound("Firma"));
    }
}
