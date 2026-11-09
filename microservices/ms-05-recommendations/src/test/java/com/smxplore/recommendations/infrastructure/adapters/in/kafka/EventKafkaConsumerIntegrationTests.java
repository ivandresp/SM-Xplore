package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.AbstractKafkaNeo4jIntegrationTest;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EventKafkaConsumerIntegrationTests extends AbstractKafkaNeo4jIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Driver driver;

    @Test
    void consumesCatalogEventsAndDeduplicatesByEventId() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = now();
        String event = siteEvent(eventId, siteId, "Playa Kafka", categoryId, now, now);

        send(EventTopics.CATALOG_SITE_CHANGED, siteId, event);
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(siteName(siteId)).isEqualTo("Playa Kafka"));

        double duplicateBefore = processedCount(EventTopics.CATALOG_SITE_CHANGED, "duplicate");
        send(EventTopics.CATALOG_SITE_CHANGED, siteId, event);
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(
                        processedCount(EventTopics.CATALOG_SITE_CHANGED, "duplicate"))
                        .isGreaterThanOrEqualTo(duplicateBefore + 1.0));

        UUID staleEventId = UUID.randomUUID();
        String staleEvent = siteEvent(
                staleEventId, siteId, "Nombre viejo", categoryId, now.plusSeconds(5), now.minusSeconds(60));
        double staleBefore = processedCount(EventTopics.CATALOG_SITE_CHANGED, "stale");
        send(EventTopics.CATALOG_SITE_CHANGED, siteId, staleEvent);
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(
                        processedCount(EventTopics.CATALOG_SITE_CHANGED, "stale"))
                        .isGreaterThanOrEqualTo(staleBefore + 1.0));

        assertThat(siteName(siteId)).isEqualTo("Playa Kafka");
        assertThat(processedOutcome(staleEventId)).isEqualTo("STALE");
        assertThat(siteCount(siteId)).isEqualTo(1);
    }

    @Test
    void consumesV2SiteAndActivityEventsWithoutDlq() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        Instant now = now();

        send(EventTopics.CATALOG_SITE_CHANGED, siteId,
                siteEventV2(UUID.randomUUID(), siteId, managerId, "Playa V2", 10.4, -75.5, now));
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(siteName(siteId)).isEqualTo("Playa V2"));

        send(EventTopics.ACTIVITIES_ACTIVITY_CHANGED, activityId,
                activityEventV2(UUID.randomUUID(), activityId, siteId, guideId,
                        "Snorkel V2", "Deportes acuáticos", now));
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(activityName(activityId)).isEqualTo("Snorkel V2"));

        UUID eventItemId = UUID.randomUUID();
        send(EventTopics.ACTIVITIES_EVENT_CHANGED, eventItemId,
                eventItemV2(UUID.randomUUID(), eventItemId, siteId, managerId,
                        "Feria V2", "Cultura", now));
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(eventItemName(eventItemId)).isEqualTo("Feria V2"));

        try (Session session = driver.session()) {
            Record record = session.run("""
                            MATCH (a:Actividad {activityId: $activityId})
                            OPTIONAL MATCH (a)-[:ES_DE_TIPO]->(c:Categoria)
                            RETURN a.name AS name,
                                   a.categoryName AS categoryName,
                                   a.latitude AS latitude,
                                   a.longitude AS longitude,
                                   c.name AS categoryNodeName
                            """, Map.of("activityId", activityId.toString()))
                    .single();
            assertThat(record.get("categoryName").asString()).isEqualTo("Deportes acuáticos");
            assertThat(record.get("latitude").asDouble()).isEqualTo(10.4);
            assertThat(record.get("longitude").asDouble()).isEqualTo(-75.5);
            assertThat(record.get("categoryNodeName").asString()).isEqualTo("Deportes acuáticos");

            Record eventRecord = session.run("""
                            MATCH (e:Evento {eventId: $eventId})
                            OPTIONAL MATCH (e)-[:ES_DE_TIPO]->(c:Categoria)
                            RETURN e.name AS name,
                                   e.categoryName AS categoryName,
                                   e.latitude AS latitude,
                                   e.longitude AS longitude,
                                   c.name AS categoryNodeName
                            """, Map.of("eventId", eventItemId.toString()))
                    .single();
            assertThat(eventRecord.get("categoryName").asString()).isEqualTo("Cultura");
            assertThat(eventRecord.get("latitude").asDouble()).isEqualTo(10.4);
            assertThat(eventRecord.get("longitude").asDouble()).isEqualTo(-75.5);
            assertThat(eventRecord.get("categoryNodeName").asString()).isEqualTo("Cultura");
        }
    }

    @Test
    void sendsMalformedEventsToDlqAndKeepsConsuming() throws Exception {
        String reviewTopic = EventTopics.REVIEWS_REVIEW_CHANGED;
        String malformed = """
                {"eventType":"reviews.review.changed","eventVersion":1}
                """;
        send(reviewTopic, UUID.randomUUID(), malformed);

        try (KafkaConsumer<String, String> consumer = dlqConsumer()) {
            consumer.subscribe(List.of(reviewTopic + ".dlq"));
            ConsumerRecord<String, String> deadLetter = awaitRecord(consumer, Duration.ofSeconds(30));
            assertThat(deadLetter.value()).isEqualTo(malformed);
        }

        assertThat(meterRegistry.counter(
                "recommendations.events.dlq", "topic", reviewTopic).count())
                .isGreaterThanOrEqualTo(1.0);

        UUID categoryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = now();
        String category = categoryEvent(eventId, categoryId, "Categoría Kafka", now);
        send(EventTopics.CATALOG_CATEGORY_CHANGED, categoryId, category);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(categoryName(categoryId)).isEqualTo("Categoría Kafka"));
    }

    @Test
    void subscribesOnlyToTheConsumedTopics() {
        var container = listenerRegistry.getListenerContainer("ms-05-domain-events");

        assertThat(container)
                .isInstanceOf(AbstractMessageListenerContainer.class);
        String[] topics = ((AbstractMessageListenerContainer<?, ?>) container)
                .getContainerProperties()
                .getTopics();

        assertThat(topics).containsExactlyInAnyOrderElementsOf(EventTopics.CONSUMED);
        assertThat(topics).doesNotContain(EventTopics.BOOKINGS_RESERVATION_CHANGED);
    }

    private void send(String topic, UUID key, String value) throws Exception {
        kafkaTemplate.send(topic, key.toString(), value).get(10, TimeUnit.SECONDS);
    }

    private String activityName(UUID activityId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (a:Actividad {activityId: $activityId})
                            RETURN a.name AS name
                            """, Map.of("activityId", activityId.toString()))
                    .list(record -> record.get("name").asString())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
    }

    private String eventItemName(UUID eventId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (e:Evento {eventId: $eventId})
                            RETURN e.name AS name
                            """, Map.of("eventId", eventId.toString()))
                    .list(record -> record.get("name").asString())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
    }

    private String siteEventV2(
            UUID eventId,
            UUID siteId,
            UUID managerId,
            String name,
            double latitude,
            double longitude,
            Instant updatedAt
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "catalog.site.changed",
                  "eventVersion": 2,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "test-correlation",
                  "producer": "ms-04",
                  "payload": {
                    "siteId": "%s",
                    "name": "%s",
                    "categoryId": "%s",
                    "managerId": "%s",
                    "latitude": %s,
                    "longitude": %s,
                    "status": "PUBLISHED",
                    "updatedAt": "%s"
                  }
                }
                """.formatted(eventId, updatedAt, siteId, siteId, name, UUID.randomUUID(),
                managerId, latitude, longitude, updatedAt);
    }

    private String eventItemV2(
            UUID eventId,
            UUID itemId,
            UUID siteId,
            UUID managerId,
            String name,
            String category,
            Instant updatedAt
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "activities.event.changed",
                  "eventVersion": 2,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "test-correlation",
                  "producer": "ms-03-activities-and-events",
                  "payload": {
                    "eventId": "%s",
                    "siteId": "%s",
                    "managerId": "%s",
                    "siteName": "Sitio de prueba",
                    "name": "%s",
                    "category": "%s",
                    "price": 25000,
                    "status": "PUBLISHED",
                    "maxCapacity": 200,
                    "startsAt": "%s",
                    "endsAt": "%s",
                    "updatedAt": "%s"
                  }
                }
                """.formatted(eventId, updatedAt, itemId, itemId, siteId, managerId, name, category,
                updatedAt.plusSeconds(3600), updatedAt.plusSeconds(7200), updatedAt);
    }

    private String activityEventV2(
            UUID eventId,
            UUID activityId,
            UUID siteId,
            UUID guideId,
            String name,
            String category,
            Instant updatedAt
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "activities.activity.changed",
                  "eventVersion": 2,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "test-correlation",
                  "producer": "ms-03-activities-and-events",
                  "payload": {
                    "activityId": "%s",
                    "siteId": "%s",
                    "guideId": "%s",
                    "name": "%s",
                    "category": "%s",
                    "price": 80000,
                    "status": "PUBLISHED",
                    "maxCapacity": 12,
                    "durationMinutes": 120,
                    "startsAt": "%s",
                    "endsAt": "%s",
                    "updatedAt": "%s"
                  }
                }
                """.formatted(eventId, updatedAt, activityId, activityId, siteId, guideId, name,
                category, updatedAt.plusSeconds(3600), updatedAt.plusSeconds(7200), updatedAt);
    }

    private String siteEvent(
            UUID eventId,
            UUID siteId,
            String name,
            UUID categoryId,
            Instant occurredAt,
            Instant updatedAt
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "catalog.site.changed",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "test-correlation",
                  "producer": "ms-04",
                  "payload": {
                    "siteId": "%s",
                    "name": "%s",
                    "categoryId": "%s",
                    "latitude": 10.0,
                    "longitude": -75.0,
                    "status": "PUBLISHED",
                    "updatedAt": "%s"
                  }
                }
                """.formatted(eventId, occurredAt, siteId, siteId, name, categoryId, updatedAt);
    }

    private String categoryEvent(UUID eventId, UUID categoryId, String name, Instant updatedAt) {        return """
                {
                  "eventId": "%s",
                  "eventType": "catalog.category.changed",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "test-correlation",
                  "producer": "ms-04",
                  "payload": {
                    "categoryId": "%s",
                    "name": "%s",
                    "scope": "GENERAL",
                    "parentId": null,
                    "updatedAt": "%s"
                  }
                }
                """.formatted(eventId, updatedAt, categoryId, categoryId, name, updatedAt);
    }

    private String siteName(UUID siteId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (s:Sitio {siteId: $siteId})
                            RETURN s.name AS name
                            """, Map.of("siteId", siteId.toString()))
                    .list(record -> record.get("name").asString())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
    }

    private long siteCount(UUID siteId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (s:Sitio {siteId: $siteId})
                            RETURN count(s) AS count
                            """, Map.of("siteId", siteId.toString()))
                    .single()
                    .get("count")
                    .asLong();
        }
    }

    private String categoryName(UUID categoryId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (c:Categoria {categoriaId: $categoryId})
                            RETURN c.name AS name
                            """, Map.of("categoryId", categoryId.toString()))
                    .list(record -> record.get("name").asString())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
    }

    private String processedOutcome(UUID eventId) {
        try (Session session = driver.session()) {
            Record record = session.run("""
                            MATCH (p:ProcessedEvent {eventId: $eventId})
                            RETURN p.outcome AS outcome
                            """, Map.of("eventId", eventId.toString()))
                    .single();
            return record.get("outcome").asString();
        }
    }

    private double processedCount(String eventType, String result) {
        return meterRegistry.counter("recommendations.events.processed",
                "event_type", eventType,
                "result", result).count();
    }

    private KafkaConsumer<String, String> dlqConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer,
            Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("No Kafka record received in " + timeout);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
