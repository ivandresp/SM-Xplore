package com.smxplore.users.infrastructure.adapter.out.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.infrastructure.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RSAKey rsaKey;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties, RSAKey rsaKey, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.rsaKey = rsaKey;
        this.clock = clock;
    }

    @Override
    public Mono<IssuedToken> issue(Usuario user) {
        return Mono.fromCallable(() -> {
            Instant now = clock.instant();
            Instant expiresAt = now.plus(properties.ttl());

            JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                    .issuer(properties.issuer())
                    .audience(List.of(properties.audience()))
                    .subject(user.id().toString())
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .id(UUID.randomUUID().toString())
                    .claim("id", user.id().toString())
                    .claim("name", user.fullName())
                    .claim("role", user.role().code())
                    .claim("ver", user.authVersion());

            if (user instanceof PrestadoraServicios provider) {
                claims.claim("providerStatus", provider.registrationStatus().name());
            }

            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                    .keyId(rsaKey.getKeyID())
                    .build();

            String token = jwtEncoder
                    .encode(JwtEncoderParameters.from(header, claims.build()))
                    .getTokenValue();

            return new IssuedToken(token, expiresAt);
        });
    }
}
