package de.rechnio.service;

import de.rechnio.dto.LoginRequest;
import de.rechnio.dto.RegisterRequest;
import de.rechnio.dto.TokenResponse;

public interface AuthService {

    /** Legt Firma und ersten Benutzer an und meldet ihn direkt an. */
    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
