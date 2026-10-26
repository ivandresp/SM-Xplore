package com.smxplore.recommendations.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
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
import java.util.List;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class JwtConfiguration {

    private final JwtProperties properties;
    private final ResourceLoader resourceLoader;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder;

        if (StringUtils.hasText(properties.getJwkSetUri())) {
            decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        } else if (StringUtils.hasText(properties.getPublicKeyLocation())) {
            decoder = NimbusReactiveJwtDecoder.withPublicKey(readPublicKey(properties.getPublicKeyLocation())).build();
        } else {
            throw new IllegalStateException(
                    "Configure app.security.jwt.jwk-set-uri (production) or "
                            + "app.security.jwt.public-key-location (local/tests). "
                            + "RS256 is required; HS256 is not supported by ms-05.");
        }

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (StringUtils.hasText(properties.getIssuer())) {
            validators.add(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        } else {
            validators.add(JwtValidators.createDefault());
        }
        if (StringUtils.hasText(properties.getAudience())) {
            validators.add(new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD,
                    audience -> audience != null && audience.contains(properties.getAudience())
            ));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
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
