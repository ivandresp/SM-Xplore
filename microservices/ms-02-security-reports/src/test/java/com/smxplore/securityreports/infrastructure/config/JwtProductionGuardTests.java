package com.smxplore.securityreports.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS02-05: por defecto la configuracion es estricta; solo los perfiles local/dev/test relajan los controles.
 */
class JwtProductionGuardTests {

    private final JwtProperties jwt = new JwtProperties();
    private final AuthVersionProperties authVersion = new AuthVersionProperties();
    private final PhotoUrlProperties photos = new PhotoUrlProperties();
    private final MockEnvironment environment = new MockEnvironment();

    @BeforeEach
    void setUp() {
        jwt.setJwkSetUri("https://ms-01:8081/.well-known/jwks.json");
        authVersion.setEnabled(true);
        photos.setAllowedHosts(List.of("cdn.example.com"));
        photos.normalize();
        environment.setProperty("springdoc.api-docs.enabled", "false");
        environment.setProperty("management.endpoints.web.exposure.include", "health,info");
    }

    @Test
    void startsWithAStrictConfigurationWhenNoProfileIsActive() {
        assertThatCode(() -> guard(true).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void relaxesTheChecksOnlyWithAnExplicitDevelopmentProfile() {
        environment.setActiveProfiles("local");
        jwt.setAllowInsecureJwks(true);
        jwt.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");
        authVersion.setEnabled(false);
        photos.setAllowAnyHost(true);

        assertThatCode(() -> guard(false).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureJwksOutsideDevelopment() {
        jwt.setAllowInsecureJwks(true);

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ALLOW_INSECURE_JWKS");
    }

    @Test
    void rejectsPlainHttpJwksOutsideDevelopment() {
        jwt.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use https");
    }

    @Test
    void rejectsDisabledAuthVersionValidationOutsideDevelopment() {
        authVersion.setEnabled(false);

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-version");
    }

    @Test
    void rejectsAdminMfaDisabledOutsideDevelopment() {
        assertThatThrownBy(() -> guard(false).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mfa.require-admin");
    }

    @Test
    void rejectsExposedMetricsOutsideDevelopment() {
        environment.setProperty("management.endpoints.web.exposure.include", "health,info,metrics,prometheus");

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metrics");
    }

    @Test
    void rejectsSpringdocEnabledOutsideDevelopment() {
        environment.setProperty("springdoc.api-docs.enabled", "true");

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("springdoc");
    }

    @Test
    void rejectsAnEmptyPhotoAllowlistOutsideDevelopment() {
        photos.setAllowedHosts(List.of());
        photos.normalize();

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-hosts");
    }

    @Test
    void rejectsAMissingIssuer() {
        jwt.setIssuer("");

        assertThatThrownBy(() -> guard(true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    private JwtProductionGuard guard(boolean requireAdminMfa) {
        return new JwtProductionGuard(jwt, authVersion, photos, environment, requireAdminMfa);
    }
}
