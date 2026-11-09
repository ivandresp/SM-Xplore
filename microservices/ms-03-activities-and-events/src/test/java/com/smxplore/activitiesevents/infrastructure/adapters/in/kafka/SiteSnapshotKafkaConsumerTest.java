package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end: a {@code catalog.site.changed} v2 written to a real broker must refresh the snapshot
 * ms-03 keeps on its events. This is the only test that exercises the {@code @KafkaListener} wiring,
 * the consumer group and the JSON decoding together.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SiteSnapshotKafkaConsumerTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final KeyPair KEYS = generateKeys();
    private static final Path PUBLIC_KEY = writePublicKey(KEYS);

    private static final String TOPIC = "catalog.site.changed";
    private static final String GROUP = "ms-03-site-snapshot-test-" + UUID.randomUUID();

    static {
        KAFKA.start();
        createTopic();
    }

    @Autowired
    private EventRepositoryPort eventRepository;

    // A raw producer instead of KafkaTemplate: the autoconfigured template is not typed String/String.
    private final Producer<String, String> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));

    private final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        var jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        var r2dbcUrl = "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("app.events.site-consumer.enabled", () -> "true");
        registry.add("app.events.site-consumer.group", () -> GROUP);
        registry.add("app.events.site-consumer.timeout", () -> "PT10S");
        registry.add("app.security.jwt.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("app.security.jwt.issuer", () -> "sm-xplore-ms-01");
        registry.add("app.security.jwt.audience", () -> "sm-xplore-services");
    }

    @Test
    void refreshesTheEventSnapshotWhenMs04PublishesANewerSite() throws Exception {
        var siteId = UUID.randomUUID();
        var formerManager = UUID.randomUUID();
        var newManager = UUID.randomUUID();
        var itemStart = Instant.now().plusSeconds(86_400);

        var saved = eventRepository.save(Event.builder()
                .siteId(siteId)
                .managerId(formerManager)
                .siteName("Nombre viejo")
                .name("Evento que espera el sitio")
                .category("Cultura")
                .status(EventStatus.PUBLISHED)
                .price(new BigDecimal("10000.00"))
                .maxCapacity(10)
                .startsAt(itemStart)
                .endsAt(itemStart.plusSeconds(3_600))
                .build()).block();
        assertThat(saved).isNotNull();

        producer.send(new ProducerRecord<>(TOPIC, siteId.toString(),
                        siteChangedMessage(siteId, newManager, "Nombre nuevo", Instant.now())))
                .get(30, TimeUnit.SECONDS);

        Event refreshed = null;
        var deadline = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(deadline)) {
            var current = eventRepository.findById(saved.getId()).block();
            if (current != null && newManager.equals(current.getManagerId())) {
                refreshed = current;
                break;
            }
            Thread.sleep(500);
        }

        assertThat(refreshed)
                .as("the consumer did not refresh the snapshot within 60 s")
                .isNotNull();
        assertThat(refreshed.getManagerId()).isEqualTo(newManager);
        assertThat(refreshed.getSiteName()).isEqualTo("Nombre nuevo");
    }

    private String siteChangedMessage(UUID siteId, UUID managerId, String name, Instant updatedAt)
            throws Exception {
        return mapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", TOPIC,
                "eventVersion", 2,
                "occurredAt", updatedAt.toString(),
                "aggregateId", siteId.toString(),
                "correlationId", UUID.randomUUID().toString(),
                "producer", "ms-04",
                "payload", Map.of(
                        "siteId", siteId.toString(),
                        "name", name,
                        "categoryId", UUID.randomUUID().toString(),
                        "managerId", managerId.toString(),
                        "latitude", 10.391,
                        "longitude", -75.479,
                        "status", "PUBLISHED",
                        "updatedAt", updatedAt.toString())));
    }

    private static KeyPair generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path writePublicKey(KeyPair keyPair) {
        try {
            var encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(keyPair.getPublic().getEncoded());
            var path = Files.createTempFile("ms-03-site-consumer-public-", ".pem");
            Files.writeString(path, "-----BEGIN PUBLIC KEY-----\n" + encoded
                    + "\n-----END PUBLIC KEY-----\n");
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void createTopic() {
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (Admin admin = Admin.create(properties)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all().get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create the Kafka topic", exception);
        }
    }
}
