package com.smxplore.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.infrastructure.adapter.out.security.JwtMfaChallengeIssuer;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import com.smxplore.users.support.FakeMfaChallengeRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

/**
 * MS01-21: el contrato JWT distingue tokens de acceso y desafíos MFA. El decodificador de acceso de ms-01 rechaza un
 * token con {@code scope=mfa}, y el decodificador de desafío lo acepta; los demás servicios deben aplicar la misma
 * regla (ver README, contrato JWT).
 */
class MfaChallengeTokenContractTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Clock CLOCK = Clock.systemUTC();
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ISSUER = "sm-xplore-ms-01";
    private static final String AUDIENCE = "sm-xplore-services";

    private JwtProperties properties;
    private RSAKey rsaKey;
    private JwtConfig config;

    @BeforeEach
    void setUp() throws Exception {
        Path keyStore = Files.createTempDirectory("ms01-jwt-contract").resolve("keys.pem");
        properties = new JwtProperties(ISSUER, AUDIENCE, Duration.ofMinutes(30), null, null, keyStore, true, false);
        rsaKey = JwtKeyStore.load(properties, true);
        config = new JwtConfig();
    }

    @Test
    void accessDecoderRejectsMfaChallengeToken() {
        String challenge = challengeToken();
        ReactiveJwtDecoder accessDecoder = config.jwtDecoder(rsaKey, properties);

        StepVerifier.create(accessDecoder.decode(challenge))
                .expectErrorSatisfies(error ->
                        assertThat(error).hasMessageContaining("El token de desafío MFA no es un token de acceso"))
                .verify();
    }

    @Test
    void challengeDecoderAcceptsMfaChallengeTokenAndAccessDecoderAcceptsAccessToken() {
        String challenge = challengeToken();
        ReactiveJwtDecoder challengeDecoder = config.mfaChallengeDecoder(rsaKey, properties);

        StepVerifier.create(challengeDecoder.decode(challenge))
                .assertNext(jwt -> assertThat(jwt.getClaimAsString("scope")).isEqualTo("mfa"))
                .verifyComplete();

        String accessToken = accessToken();
        ReactiveJwtDecoder accessDecoder = config.jwtDecoder(rsaKey, properties);

        StepVerifier.create(accessDecoder.decode(accessToken))
                .assertNext(jwt -> {
                    assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
                    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
                    assertThat(jwt.hasClaim("scope")).isFalse();
                })
                .verifyComplete();
    }

    private String challengeToken() {
        Admin admin = Admin.create(
                USER_ID,
                "Ada Admin",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash-de-prueba"),
                "CC",
                "1234567890",
                NOW);
        JwtMfaChallengeIssuer issuer = new JwtMfaChallengeIssuer(
                config.jwtEncoder(rsaKey),
                properties,
                new MfaProperties(false, Duration.ofMinutes(5), "SM-Xplore", null),
                new FakeMfaChallengeRepository(),
                rsaKey,
                CLOCK);
        return issuer.issue(admin).block().token();
    }

    private String accessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(USER_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(30)))
                .claim("role", "ADMIN")
                .claim("ver", 0)
                .claim("mfa", true)
                .build();
        return config.jwtEncoder(rsaKey)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
