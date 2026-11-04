package com.smxplore.sitesandservices;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

public abstract class AbstractKafkaMongoPostgresIntegrationTest extends AbstractMongoPostgresIntegrationTest {

    public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.outbox.enabled", () -> "true");
        registry.add("app.outbox.poll-interval-ms", () -> "100");
    }
}
