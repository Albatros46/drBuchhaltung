package de.rechnio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rechnio.jwt")
public record JwtProperties(String secret, int ttlHours) {
}
