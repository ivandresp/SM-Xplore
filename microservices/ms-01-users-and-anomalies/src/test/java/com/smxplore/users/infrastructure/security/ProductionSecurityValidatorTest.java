package com.smxplore.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.infrastructure.config.BootstrapAdminProperties;
import com.smxplore.users.infrastructure.config.CorsProperties;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {

    private static final MfaProperties MFA = new MfaProperties(true, Duration.ofMinutes(5), "SM-Xplore", null);
    private static final CorsProperties CORS = new CorsProperties(List.of("https://app.smxplore.com"));

    @Test
    void acceptsASecureConfiguration() {
        ProductionSecurityValidator validator = validator(
                new JwtProperties("i", "a", Duration.ofHours(1), "pem", "pem", null, false),
                new BootstrapAdminProperties(false, "admin@smxplore.com", "ChangeMe123!", "Admin"));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsGeneratedKeysWithoutConfiguredPem() {
        ProductionSecurityValidator validator = validator(
                new JwtProperties("i", "a", Duration.ofHours(1), null, null, null, true),
                new BootstrapAdminProperties(false, "admin@smxplore.com", "ChangeMe123!", "Admin"));

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTheDefaultBootstrapPassword() {
        ProductionSecurityValidator validator = validator(
                new JwtProperties("i", "a", Duration.ofHours(1), "pem", "pem", null, false),
                new BootstrapAdminProperties(true, "admin@smxplore.com", "ChangeMe123!", "Admin"));

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsDefaultPasswordWhenBootstrapIsDisabled() {
        ProductionSecurityValidator validator = validator(
                new JwtProperties("i", "a", Duration.ofHours(1), "pem", "pem", null, false),
                new BootstrapAdminProperties(false, "admin@smxplore.com", "ChangeMe123!", "Admin"));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static ProductionSecurityValidator validator(JwtProperties jwt, BootstrapAdminProperties admin) {
        return new ProductionSecurityValidator(jwt, admin, CORS, MFA);
    }
}
