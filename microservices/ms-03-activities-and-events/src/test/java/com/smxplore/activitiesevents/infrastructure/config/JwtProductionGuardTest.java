package com.smxplore.activitiesevents.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProductionGuardTest {

    @Test
    void rejectsTheInsecureFlagInProd() {
        var properties = new JwtProperties();
        properties.setAllowInsecureJwks(true);

        assertThatThrownBy(() -> new JwtProductionGuard(properties, prod()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ALLOW_INSECURE_JWKS");
    }

    @Test
    void rejectsPlainHttpInProd() {
        var properties = new JwtProperties();
        properties.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        assertThatThrownBy(() -> new JwtProductionGuard(properties, prod()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
    }

    @Test
    void allowsTheLocalStackWithTheLocalProfile() {
        var properties = new JwtProperties();
        properties.setAllowInsecureJwks(true);
        properties.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        assertThatCode(() -> new JwtProductionGuard(properties, local()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTheInsecureFlagWithoutALocalProfile() {
        var properties = new JwtProperties();
        properties.setAllowInsecureJwks(true);
        properties.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        // Un contenedor sin SPRING_PROFILES_ACTIVE no puede aceptar un JWKS por HTTP.
        assertThatThrownBy(() -> new JwtProductionGuard(properties, new StandardEnvironment())
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development flag");
    }

    @Test
    void requiresIssuerAndAudienceInProd() {
        var properties = new JwtProperties();
        properties.setJwkSetUri("https://ms-01/.well-known/jwks.json");
        properties.setIssuer("");
        properties.setAudience("");

        assertThatThrownBy(() -> new JwtProductionGuard(properties, prod()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    private static StandardEnvironment prod() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private static StandardEnvironment local() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("local");
        return environment;
    }
}
