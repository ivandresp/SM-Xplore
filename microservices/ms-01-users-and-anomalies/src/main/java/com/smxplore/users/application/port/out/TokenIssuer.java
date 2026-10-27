package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import reactor.core.publisher.Mono;

public interface TokenIssuer {

    Mono<IssuedToken> issue(Usuario user, boolean mfaVerified);

    record IssuedToken(String value, Instant expiresAt) {}
}
