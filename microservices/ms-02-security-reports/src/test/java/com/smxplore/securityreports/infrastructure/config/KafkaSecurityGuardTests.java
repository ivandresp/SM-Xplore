package com.smxplore.securityreports.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS02-09: fuera de local/dev/test el productor de Kafka debe viajar cifrado.
 */
class KafkaSecurityGuardTests {

    @Test
    void allowsPlainTextInsideARelaxedProfile() {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "local");

        assertThatCode(() -> new KafkaSecurityGuard(environment, "PLAINTEXT", "").afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainTextOutsideARelaxedProfile() {
        assertThatThrownBy(() -> new KafkaSecurityGuard(new MockEnvironment(), "PLAINTEXT", "")
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAFKA_SECURITY_PROTOCOL must be SSL or SASL_SSL");
    }

    @Test
    void rejectsAnUnknownProtocol() {
        assertThatThrownBy(() -> new KafkaSecurityGuard(new MockEnvironment(), "SASL_PLAINTEXT",
                "org.apache.kafka.common.security.plain.PlainLoginModule required;").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be SSL or SASL_SSL");
    }

    @Test
    void acceptsSslWithoutAnAllowlistException() {
        assertThatCode(() -> new KafkaSecurityGuard(new MockEnvironment(), "SSL", "").afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void requiresSaslCredentialsWhenSaslIsUsed() {
        assertThatThrownBy(() -> new KafkaSecurityGuard(new MockEnvironment(), "SASL_SSL", "")
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAFKA_SASL_JAAS_CONFIG");

        assertThatCode(() -> new KafkaSecurityGuard(new MockEnvironment(), "SASL_SSL",
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"u\" password=\"p\";")
                .afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
