package de.rechnio.service.impl;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.config.JwtProperties;
import de.rechnio.dto.LoginRequest;
import de.rechnio.dto.RegisterRequest;
import de.rechnio.dto.TokenResponse;
import de.rechnio.entity.AppUser;
import de.rechnio.entity.Organization;
import de.rechnio.exception.ApiException;
import de.rechnio.repository.AppUserRepository;
import de.rechnio.repository.OrganizationRepository;
import de.rechnio.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(AppUserRepository users, OrganizationRepository organizations,
                           PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Diese E-Mail-Adresse ist bereits registriert");
        }
        Organization org = organizations.save(new Organization(req.companyName()));
        AppUser user = users.save(new AppUser(org.getId(), req.email().toLowerCase(), passwordEncoder.encode(req.password())));
        return new TokenResponse(token(user), user.getEmail(), org.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "E-Mail oder Passwort falsch"));
        String company = organizations.findById(user.getOrganizationId()).map(Organization::getName).orElse("");
        return new TokenResponse(token(user), user.getEmail(), company);
    }

    private String token(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rechnio")
                .subject(user.getId().toString())
                .claim("org", user.getOrganizationId().toString())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(jwtProperties.ttlHours())))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
