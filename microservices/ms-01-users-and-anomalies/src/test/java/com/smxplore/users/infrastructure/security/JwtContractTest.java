package com.smxplore.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.infrastructure.adapter.out.security.JwtTokenIssuer;
import com.smxplore.users.infrastructure.config.JwtProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

class JwtContractTest {

    @Test
    void issuedTokenContainsTheClaimsExpectedByOtherServices() throws Exception {
        JwtProperties properties = new JwtProperties(
                "sm-xplore-ms-01", "sm-xplore-services", Duration.ofMinutes(60), null, null, null, true);
        RSAKey rsaKey = rsaKey();

        JwtConfig config = new JwtConfig();
        JwtEncoder encoder = config.jwtEncoder(rsaKey);
        ReactiveJwtDecoder decoder = config.jwtDecoder(rsaKey, properties);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtTokenIssuer issuer = new JwtTokenIssuer(encoder, properties, rsaKey, clock);

        Turista user = Turista.register(
                UUID.randomUUID(),
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "CC",
                "1234567890",
                "es",
                null,
                "Colombia",
                clock.instant());

        TokenIssuer.IssuedToken issued = issuer.issue(user, false).block();
        Jwt jwt = decoder.decode(issued.value()).block();

        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsString("id")).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ada Turista");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TURISTA");
        assertThat(jwt.getClaimAsBoolean("mfa")).isFalse();
        assertThat(((Number) jwt.getClaim("ver")).longValue()).isZero();
        assertThat(jwt.getAudience()).containsExactly("sm-xplore-services");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("sm-xplore-ms-01");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(60)));
    }

    private static RSAKey rsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
    }
}
