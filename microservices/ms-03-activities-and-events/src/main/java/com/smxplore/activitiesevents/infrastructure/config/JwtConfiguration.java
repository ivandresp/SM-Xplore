package com.smxplore.activitiesevents.infrastructure.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(5);

    private final JwtProperties properties;
    private final ResourceLoader resourceLoader;

    @Bean
    public ReactiveJwtDecoder jwtDecoder(AuthVersionValidator authVersionValidator) {
        ReactiveJwtDecoder delegate = baseJwtDecoder();
        return token -> delegate.decode(token).flatMap(authVersionValidator::validate);
    }

    /** Decoder without the per-request validators, kept for unit tests and direct construction. */
    public ReactiveJwtDecoder jwtDecoder() {
        return baseJwtDecoder();
    }

    private ReactiveJwtDecoder baseJwtDecoder() {
        requireText(properties.getIssuer(), "app.security.jwt.issuer");
        requireText(properties.getAudience(), "app.security.jwt.audience");

        var decoder = buildDecoder();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        validators.add(new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(properties.getAudience())));
        validators.add(requireRs256());
        validators.add(rejectMfaChallengeTokens());

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private NimbusReactiveJwtDecoder buildDecoder() {
        if (StringUtils.hasText(properties.getJwkSetUri())) {
            var jwkSetUri = validateJwksUri(properties.getJwkSetUri());
            var httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                    .responseTimeout(JWKS_TIMEOUT);
            var webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri.toString())
                    .webClient(webClient)
                    .build();
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

    private static void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(property + " is required.");
        }
    }

    /**
     * Plain HTTP is only accepted for a loopback JWKS (local development) or when
     * {@code app.security.jwt.allow-insecure-jwks} is explicitly enabled for a local
     * stack (for example {@code http://ms-01:8081} inside Compose). Any remote key
     * source must use HTTPS so a network attacker cannot substitute signing keys.
     */
    private URI validateJwksUri(String value) {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.security.jwt.jwk-set-uri is not a valid URI.", exception);
        }
        var host = uri.getHost();
        if (host == null) {
            throw new IllegalStateException("app.security.jwt.jwk-set-uri must include a host.");
        }
        var loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback && !properties.isAllowInsecureJwks()) {
            throw new IllegalStateException(
                    "app.security.jwt.jwk-set-uri must use https outside loopback.");
        }
        return uri;
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
            var isMfaChallenge = containsMfa(jwt.getClaim("scope"))
                    || containsMfa(jwt.getClaim("scp"));
            if (isMfaChallenge) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "MFA challenge tokens are not access tokens.", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static boolean containsMfa(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> "mfa".equalsIgnoreCase(String.valueOf(value)));
        }
        if (claim instanceof String value) {
            return Arrays.stream(value.trim().split("\\s+"))
                    .anyMatch(token -> "mfa".equalsIgnoreCase(token));
        }
        return false;
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