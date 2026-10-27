package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.port.out.MfaChallengeVerifier;
import com.smxplore.users.domain.exception.InvalidMfaChallengeException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JwtMfaChallengeVerifier implements MfaChallengeVerifier {

    private final ReactiveJwtDecoder decoder;

    public JwtMfaChallengeVerifier(@Qualifier("mfaChallengeDecoder") ReactiveJwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Mono<UUID> verify(String token) {
        if (token == null || token.isBlank()) {
            return Mono.error(new InvalidMfaChallengeException());
        }
        return decoder.decode(token)
                .map(jwt -> UUID.fromString(jwt.getSubject()))
                .onErrorMap(
                        error -> !(error instanceof InvalidMfaChallengeException),
                        error -> new InvalidMfaChallengeException());
    }
}
