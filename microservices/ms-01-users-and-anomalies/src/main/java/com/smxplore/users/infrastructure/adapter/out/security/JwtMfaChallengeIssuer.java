package com.smxplore.users.infrastructure.adapter.out.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.application.port.out.MfaChallengeIssuer;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
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
public class JwtMfaChallengeIssuer implements MfaChallengeIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final MfaProperties mfaProperties;
    private final RSAKey rsaKey;
    private final Clock clock;

    public JwtMfaChallengeIssuer(
            JwtEncoder jwtEncoder, JwtProperties properties, MfaProperties mfaProperties, RSAKey rsaKey, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.mfaProperties = mfaProperties;
        this.rsaKey = rsaKey;
        this.clock = clock;
    }

    @Override
    public Mono<IssuedChallenge> issue(Usuario user) {
        return Mono.fromCallable(() -> {
            Instant now = clock.instant();
            Instant expiresAt = now.plus(mfaProperties.challengeTtl());

            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(properties.issuer())
                    .audience(List.of(properties.audience()))
                    .subject(user.id().toString())
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .id(UUID.randomUUID().toString())
                    .claim("scope", "mfa")
                    .claim("ver", user.authVersion())
                    .build();

            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                    .keyId(rsaKey.getKeyID())
                    .build();

            String token =
                    jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
            return new IssuedChallenge(token, expiresAt);
        });
    }
}
