package com.smxplore.securityreports.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTests {

    @Test
    void rejectsMissingSecret() {
        var properties = new JwtProperties();
        properties.setSecret("");

        var configuration = new JwtConfiguration(properties);

        assertThatThrownBy(configuration::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is required");
    }

    @Test
    void rejectsDefaultDevelopmentSecret() {
        var properties = new JwtProperties();
        properties.setSecret("dev-secret-change-me-32-bytes-minimum!");

        var configuration = new JwtConfiguration(properties);

        assertThatThrownBy(configuration::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default development JWT secret");
    }

    @Test
    void rejectsShortSecret() {
        var properties = new JwtProperties();
        properties.setSecret("too-short");

        var configuration = new JwtConfiguration(properties);

        assertThatThrownBy(configuration::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void createsDecoderWithValidConfiguration() {
        var properties = new JwtProperties();
        properties.setSecret("test-secret-for-integration-tests-32-bytes-minimum!");
        properties.setIssuer("https://test.local");

        var configuration = new JwtConfiguration(properties);

        assertThat(configuration.jwtDecoder()).isNotNull();
    }
}
