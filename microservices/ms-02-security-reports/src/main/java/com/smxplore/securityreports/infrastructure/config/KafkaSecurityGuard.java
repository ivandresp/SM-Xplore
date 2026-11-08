package com.smxplore.securityreports.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Falla al arrancar si fuera de un perfil de desarrollo el productor de Kafka viaja sin cifrar.
 *
 * <p>MS02-09: el evento {@code security.report.changed} incluye datos personales (quien reporta, titulo y
 * ubicacion aproximada); en PLAINTEXT cualquier cliente de la red puede leerlos. El contrato v1 no permite
 * quitar esos campos, asi que el control compensatorio es exigir TLS (o SASL sobre TLS) en produccion.
 */
@Slf4j
@Component
public class KafkaSecurityGuard implements InitializingBean {

    private static final Profiles RELAXED_PROFILES = Profiles.of("local", "dev", "test");
    private static final Set<String> ENCRYPTED_PROTOCOLS = Set.of("SSL", "SASL_SSL");

    private final Environment environment;
    private final String protocol;
    private final String saslJaasConfig;

    public KafkaSecurityGuard(Environment environment,
                              @Value("${spring.kafka.security.protocol:PLAINTEXT}") String protocol,
                              @Value("${spring.kafka.properties.sasl.jaas.config:}") String saslJaasConfig) {
        this.environment = environment;
        this.protocol = protocol;
        this.saslJaasConfig = saslJaasConfig;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(RELAXED_PROFILES)) {
            log.warn("Kafka transport security relaxed by a local/dev/test profile: KAFKA_SECURITY_PROTOCOL={}",
                    protocol);
            return;
        }
        var normalized = protocol == null ? "" : protocol.trim().toUpperCase(Locale.ROOT);
        require(ENCRYPTED_PROTOCOLS.contains(normalized),
                "KAFKA_SECURITY_PROTOCOL must be SSL or SASL_SSL outside the local, dev or test profiles:"
                        + " the security.report.changed event carries personal data.");
        if ("SASL_SSL".equals(normalized)) {
            require(StringUtils.hasText(saslJaasConfig),
                    "KAFKA_SASL_JAAS_CONFIG is required when KAFKA_SECURITY_PROTOCOL=SASL_SSL outside the"
                            + " local, dev or test profiles.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
