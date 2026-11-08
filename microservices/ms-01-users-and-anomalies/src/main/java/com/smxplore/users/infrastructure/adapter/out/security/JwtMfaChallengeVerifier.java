package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.dto.MfaChallengeContent;
import com.smxplore.users.application.port.out.MfaChallengeVerifier;
import com.smxplore.users.domain.exception.InvalidMfaChallengeException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public Mono<MfaChallengeContent> verify(String token) {
        if (token == null || token.isBlank()) {
            return Mono.error(new InvalidMfaChallengeException());
        }
        return decoder.decode(token)
                .map(this::content)
                .onErrorMap(
                        error -> !(error instanceof InvalidMfaChallengeException),
                        error -> new InvalidMfaChallengeException());
    }

    /**
     * MS01-08: extrae también la versión de sesión ({@code ver}) y el {@code jti} del desafío. Antes solo se leía el
     * sujeto, así que no se podía comparar la versión ni consumir el desafío una única vez.
     */
    private MfaChallengeContent content(Jwt jwt) {
        Object version = jwt.getClaim("ver");
        String id = jwt.getId();
        if (!(version instanceof Number number) || id == null || id.isBlank()) {
            throw new InvalidMfaChallengeException();
        }
        return new MfaChallengeContent(UUID.fromString(jwt.getSubject()), number.longValue(), UUID.fromString(id));
    }
}
