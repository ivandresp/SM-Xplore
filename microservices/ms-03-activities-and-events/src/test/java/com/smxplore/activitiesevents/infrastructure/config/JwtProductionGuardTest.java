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
    void allowsTheLocalStackOutsideProd() {
        var properties = new JwtProperties();
        properties.setAllowInsecureJwks(true);
        properties.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        assertThatCode(() -> new JwtProductionGuard(properties, new StandardEnvironment()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static StandardEnvironment prod() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
