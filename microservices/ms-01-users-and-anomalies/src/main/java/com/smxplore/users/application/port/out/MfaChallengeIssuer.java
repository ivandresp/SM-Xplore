package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import reactor.core.publisher.Mono;

public interface MfaChallengeIssuer {

    Mono<IssuedChallenge> issue(Usuario user);

    record IssuedChallenge(String token, Instant expiresAt) {}
}
