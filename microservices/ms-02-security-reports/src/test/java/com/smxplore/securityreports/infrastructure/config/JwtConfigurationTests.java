package com.smxplore.securityreports.infrastructure.config;

import com.smxplore.securityreports.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTests {

    @Test
    void decodesAValidRs256Token() {
        var properties = properties();
        var decoder = new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder();

        var jwt = decoder.decode(token("TOURIST", null)).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("66666666-6666-6666-6666-666666666601");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TOURIST");
    }

    @Test
    void rejectsMfaChallengeTokens() {
        var decoder = new JwtConfiguration(properties(), new DefaultResourceLoader()).jwtDecoder();

        assertThatThrownBy(() -> decoder.decode(token("TOURIST", "mfa")).block())
                .hasMessageContaining("MFA challenge");
    }

    @Test
    void rejectsMissingDecoderConfiguration() {
        var properties = new JwtProperties();
        properties.setIssuer("sm-xplore-ms-01");

        var configuration = new JwtConfiguration(properties, new DefaultResourceLoader());

        assertThatThrownBy(configuration::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RS256");
    }

    @Test
    void rejectsMissingIssuer() {
        var properties = new JwtProperties();
        properties.setIssuer("");

        var configuration = new JwtConfiguration(properties, new DefaultResourceLoader());

        assertThatThrownBy(configuration::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    private static JwtProperties properties() {
        var properties = new JwtProperties();
        properties.setPublicKeyLocation("classpath:keys/test-public.pem");
        properties.setIssuer("sm-xplore-ms-01");
        properties.setAudience("sm-xplore-services");
        return properties;
    }

    private static String token(String role, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("sm-xplore-ms-01")
                .audience(List.of("sm-xplore-services"))
                .subject("66666666-6666-6666-6666-666666666601")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", role)
                .claim("name", "Turista");
        if (scope != null) {
            claims.claim("scope", scope);
        }
        JwtEncoder encoder = new NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(
                        new com.nimbusds.jose.jwk.JWKSet(TestKeys.rsaKey())));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build())).getTokenValue();
    }
}
