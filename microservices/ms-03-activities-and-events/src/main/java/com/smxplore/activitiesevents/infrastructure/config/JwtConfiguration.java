package com.smxplore.activitiesevents.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * RS256 JWT decoder aligned with ms-01. The service trusts either the JWKS endpoint
 * published by ms-01 or a PEM public key; shared HS256 secrets are not supported.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class JwtConfiguration {

    private final JwtProperties properties;
    private final ResourceLoader resourceLoader;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        if (!StringUtils.hasText(properties.getIssuer())) {
            throw new IllegalStateException("app.security.jwt.issuer is required.");
        }
        var decoder = buildDecoder();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (StringUtils.hasText(properties.getIssuer())) {
            validators.add(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        } else {
            validators.add(JwtValidators.createDefault());
        }
        if (StringUtils.hasText(properties.getAudience())) {
            validators.add(new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD,
                    audience -> audience != null && audience.contains(properties.getAudience())));
        }
        validators.add(requireRs256());
        validators.add(rejectMfaChallengeTokens());

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private NimbusReactiveJwtDecoder buildDecoder() {
        if (StringUtils.hasText(properties.getJwkSetUri())) {
            return NimbusReactiveJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        }
        if (StringUtils.hasText(properties.getPublicKeyLocation())) {
            return NimbusReactiveJwtDecoder.withPublicKey(
                    readPublicKey(properties.getPublicKeyLocation())).build();
        }
        throw new IllegalStateException(
                "Configure app.security.jwt.jwk-set-uri (production) or "
                        + "app.security.jwt.public-key-location (local/tests). "
                        + "RS256 is required; HS256 is not supported by ms-03.");
    }

    /** Only RS256 is accepted; this blocks RSA algorithm substitution on the JWKS path. */
    static OAuth2TokenValidator<Jwt> requireRs256() {
        return jwt -> "RS256".equals(jwt.getHeaders().get("alg"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Only RS256 tokens are accepted.", null));
    }

    /**
     * MFA challenge tokens are short-lived tokens with scope=mfa, not access tokens.
     * Every service except ms-01 must reject them.
     */
    private static OAuth2TokenValidator<Jwt> rejectMfaChallengeTokens() {
        return jwt -> {
            var scope = jwt.getClaim("scope");
            var isMfaChallenge = (scope instanceof Collection<?> values && values.contains("mfa"))
                    || (scope instanceof String value
                    && List.of(value.trim().split("\\s+")).contains("mfa"));
            if (isMfaChallenge) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "MFA challenge tokens are not access tokens.", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private RSAPublicKey readPublicKey(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream input = resource.getInputStream()) {
            String pem = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read RSA public key from " + location, exception);
        }
    }
}
