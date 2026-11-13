package com.smxplore.users;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            // B3: el publisher de users.account.blocked queda apagado en application-test.yml para
            // que los flujos no intenten hablar con Kafka. Este contexto lo enciende a proposito:
            // si falta el auto-configurador (spring-boot-starter-kafka) o el ObjectMapper de
            // Jackson 3, el contexto no arranca y el fallo se ve aqui y no en el Stack CI.
            "app.events.account-publisher.enabled=true"
        })
class ApplicationContextTest {

    @Test
    void contextLoadsWithReactiveBeans() {
        // Fails if the R2DBC/transaction/security wiring is inconsistent.
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubR2dbcConfig {

        @Bean
        ConnectionFactory connectionFactory() {
            return new ConnectionFactory() {
                @Override
                public Publisher<? extends Connection> create() {
                    return Mono.empty();
                }

                @Override
                public ConnectionFactoryMetadata getMetadata() {
                    return () -> "PostgreSQL";
                }
            };
        }
    }
}
