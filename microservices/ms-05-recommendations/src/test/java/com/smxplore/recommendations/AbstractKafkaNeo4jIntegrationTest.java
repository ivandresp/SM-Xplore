package com.smxplore.recommendations;

import com.smxplore.recommendations.domain.model.event.EventTopics;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * One Kafka container for the whole test JVM, next to the shared Neo4j container.
 * Topics are created explicitly so tests do not depend on broker auto-creation.
 */
public abstract class AbstractKafkaNeo4jIntegrationTest extends AbstractNeo4jIntegrationTest {

    public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");
    private static final String GROUP_ID = "ms-05-test-" + UUID.randomUUID();

    static {
        KAFKA.start();
        createTopics();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> GROUP_ID);
        registry.add("app.events.consumer.enabled", () -> "true");
        registry.add("app.events.consumer.max-retries", () -> "1");
        registry.add("app.events.consumer.initial-backoff", () -> "20ms");
        registry.add("app.events.consumer.max-backoff", () -> "100ms");
        registry.add("app.events.consumer.processing-timeout", () -> "10s");
    }

    private static void createTopics() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : EventTopics.CONSUMED) {
            topics.add(new NewTopic(topic, 1, (short) 1));
            topics.add(new NewTopic(topic + ".dlq", 1, (short) 1));
        }
        try (Admin admin = Admin.create(properties)) {
            admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create Kafka topics", exception);
        }
    }
}
