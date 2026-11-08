package com.smxplore.activitiesevents.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProductionGuardTest {

    private static final String AUTH_VERSION_ENABLED = "app.security.auth-version.enabled";
    private static final String MFA_REQUIRE_ADMIN = "app.security.mfa.require-admin";

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

        var environment = local();
        environment.setProperty(AUTH_VERSION_ENABLED, "false");
        environment.setProperty(MFA_REQUIRE_ADMIN, "false");

        assertThatCode(() -> new JwtProductionGuard(properties, environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTheInsecureFlagWithoutALocalProfile() {
        var properties = new JwtProperties();
        properties.setAllowInsecureJwks(true);
        properties.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        // Un contenedor sin SPRING_PROFILES_ACTIVE no puede aceptar un JWKS por HTTP.
        assertThatThrownBy(() -> new JwtProductionGuard(properties, new MockEnvironment())
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

    @Test
    void rejectsDisabledMfaOutsideDevelopmentProfiles() {
        var properties = new JwtProperties();
        properties.setJwkSetUri("https://ms-01/.well-known/jwks.json");
        properties.setIssuer("sm-xplore-ms-01");
        properties.setAudience("sm-xplore-services");

        var environment = prod();
        environment.setProperty(MFA_REQUIRE_ADMIN, "false");

        assertThatThrownBy(() -> new JwtProductionGuard(properties, environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA_REQUIRE_ADMIN");
    }

    @Test
    void rejectsDisabledAuthVersionOutsideDevelopmentProfiles() {
        var properties = new JwtProperties();
        properties.setJwkSetUri("https://ms-01/.well-known/jwks.json");
        properties.setIssuer("sm-xplore-ms-01");
        properties.setAudience("sm-xplore-services");

        var environment = prod();
        environment.setProperty(AUTH_VERSION_ENABLED, "false");

        assertThatThrownBy(() -> new JwtProductionGuard(properties, environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_VERSION_ENABLED");
    }

    @Test
    void rejectsTheControlsDisabledWithoutAnyProfile() {
        var properties = new JwtProperties();
        properties.setJwkSetUri("https://ms-01/.well-known/jwks.json");
        properties.setIssuer("sm-xplore-ms-01");
        properties.setAudience("sm-xplore-services");

        // Sin perfil no hay relajacion posible: los dos controles deben estar encendidos.
        assertThatThrownBy(() -> new JwtProductionGuard(properties, new MockEnvironment())
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_VERSION_ENABLED");
    }

    private static MockEnvironment prod() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private static MockEnvironment local() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("local");
        return environment;
    }

    private static MockEnvironment secureEnvironment() {
        var environment = new MockEnvironment();
        environment.setProperty(AUTH_VERSION_ENABLED, "true");
        environment.setProperty(MFA_REQUIRE_ADMIN, "true");
        return environment;
    }
}
