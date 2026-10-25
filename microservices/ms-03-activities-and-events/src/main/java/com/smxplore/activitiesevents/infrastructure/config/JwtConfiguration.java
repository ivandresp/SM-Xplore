package com.smxplore.activitiesevents.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HS256 JWT decoder shared by every profile. Mirrors the ms-02 contract so the
 * service can be integrated with the same token source later on.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    public static final String ROLE_CLAIM = "role";

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        validateSecret(jwtProperties.getSecret());
        validateIssuer(jwtProperties.getIssuer());

        var key = new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()));
        if (jwtProperties.getAudience() != null && !jwtProperties.getAudience().isBlank()) {
            validators.add(new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD,
                    audience -> audience != null && audience.contains(jwtProperties.getAudience())
            ));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is required. Configure app.security.jwt.secret with a secret of at least 32 bytes.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 bytes for HS256.");
        }
    }

    private static void validateIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.security.jwt.issuer is required.");
        }
    }
}
