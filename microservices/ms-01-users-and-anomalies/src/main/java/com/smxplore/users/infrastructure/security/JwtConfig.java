package com.smxplore.users.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.infrastructure.config.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import reactor.core.publisher.Flux;

@Configuration
public class JwtConfig {

    private static final String MFA_SCOPE = "mfa";

    @Bean
    public RSAKey rsaKey(JwtProperties properties, Environment environment) {
        return JwtKeyStore.load(properties, environment.acceptsProfiles(Profiles.of("dev")));
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /**
     * Decodificador de los tokens de acceso. Rechaza los tokens de desafío MFA para que no puedan usarse como
     * credencial de acceso.
     */
    @Primary
    @Bean
    public ReactiveJwtDecoder jwtDecoder(RSAKey rsaKey, JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = decoderFor(rsaKey);
        OAuth2TokenValidator<Jwt> notChallenge = token -> MFA_SCOPE.equals(token.getClaimAsString("scope"))
                ? OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "El token de desafío MFA no es un token de acceso", null))
                : OAuth2TokenValidatorResult.success();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceValidator(properties),
                notChallenge));
        return decoder;
    }

    /**
     * Decodificador exclusivo de los desafíos MFA emitidos tras validar la contraseña.
     */
    @Bean
    public ReactiveJwtDecoder mfaChallengeDecoder(RSAKey rsaKey, JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = decoderFor(rsaKey);
        OAuth2TokenValidator<Jwt> challengeOnly = token -> MFA_SCOPE.equals(token.getClaimAsString("scope"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "El token no es un desafío MFA", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceValidator(properties),
                challengeOnly));
        return decoder;
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? Flux.empty() : Flux.just(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    private static NimbusReactiveJwtDecoder decoderFor(RSAKey rsaKey) {
        try {
            return NimbusReactiveJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
                    .build();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("No se pudo configurar el decodificador JWT", exception);
        }
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(JwtProperties properties) {
        return token -> token.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "El token no está dirigido a esta audiencia", null));
    }
}
