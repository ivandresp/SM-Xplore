package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.Usuario;

public interface TokenIssuer {

    IssuedToken issue(Usuario user);

    record IssuedToken(String value, java.time.Instant expiresAt) {}
}
