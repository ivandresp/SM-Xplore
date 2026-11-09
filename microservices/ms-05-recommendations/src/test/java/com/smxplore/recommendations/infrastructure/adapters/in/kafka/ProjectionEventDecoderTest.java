package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;
import com.smxplore.recommendations.domain.model.catalog.ZoneStatus;
import com.smxplore.recommendations.domain.model.event.ActivityChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ReviewChangedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.model.event.ZoneChangedEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionEventDecoderTest {

    /**
     * Fixed clock after every contract fixture date, so the future-skew guard (MS05-01) does
     * not reject the examples and the regression tests can use timestamps relative to it.
     */
    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventConsumerProperties properties = new EventConsumerProperties();
    private final ProjectionEventDecoder decoder = new ProjectionEventDecoder(
            objectMapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Path contractsDirectory =
            Path.of(System.getProperty("contracts.dir", "../../contracts/events"));

    @Test
    void acceptsAnExampleWithoutKafkaKey() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        String json = Files.readString(examplePath(topic));

        var event = decoder.decode(topic, null, json);

        assertThat(event.eventType()).isEqualTo(topic);
    }

    @Test
    void rejectsAKeyThatIsNotTheAggregateId() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        String json = Files.readString(examplePath(topic));

        assertThatThrownBy(() -> decoder.decode(topic, "another-id", json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void rejectsATopicThatDoesNotMatchTheEventType() throws Exception {
        String json = Files.readString(examplePath(EventTopics.CATALOG_SITE_CHANGED));

        assertThatThrownBy(() -> decoder.decode(
                EventTopics.REVIEWS_REVIEW_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("does not match topic");
    }

    @Test
    void rejectsAnUnsupportedEventVersion() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(examplePath(topic, 2)));
        root.put("eventVersion", 3);

        assertThatThrownBy(() -> decoder.decode(topic, null, objectMapper.writeValueAsString(root)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void acceptsV2SiteActivityAndEventExamples() throws Exception {
        String siteJson = Files.readString(examplePath(EventTopics.CATALOG_SITE_CHANGED, 2));
        var site = decoder.decode(
                EventTopics.CATALOG_SITE_CHANGED,
                objectMapper.readTree(siteJson).get("aggregateId").asString(),
                siteJson);
        assertThat(site).isInstanceOf(SiteChangedEvent.class);
        assertThat(site.metadata().eventVersion()).isEqualTo(2);

        String activityJson = Files.readString(examplePath(EventTopics.ACTIVITIES_ACTIVITY_CHANGED, 2));
        var activity = decoder.decode(
                EventTopics.ACTIVITIES_ACTIVITY_CHANGED,
                objectMapper.readTree(activityJson).get("aggregateId").asString(),
                activityJson);
        assertThat(activity).isInstanceOf(ActivityChangedEvent.class);
        ActivityChangedEvent activityEvent = (ActivityChangedEvent) activity;
        assertThat(activityEvent.categoryName()).isEqualTo("Deportes acuáticos");
        assertThat(activityEvent.categoryId()).isNotNull();
        assertThat(activityEvent.latitude()).isNull();
        assertThat(activityEvent.longitude()).isNull();
        assertThat(activityEvent.status()).isEqualTo(ContentStatus.PUBLISHED);

        String eventJson = Files.readString(examplePath(EventTopics.ACTIVITIES_EVENT_CHANGED, 2));
        var eventItem = decoder.decode(
                EventTopics.ACTIVITIES_EVENT_CHANGED,
                objectMapper.readTree(eventJson).get("aggregateId").asString(),
                eventJson);
        assertThat(eventItem).isInstanceOf(EventItemChangedEvent.class);
        EventItemChangedEvent eventItemEvent = (EventItemChangedEvent) eventItem;
        assertThat(eventItemEvent.categoryName()).isEqualTo("Cultura");
        assertThat(eventItemEvent.categoryId()).isNotNull();
        assertThat(eventItemEvent.status()).isEqualTo(ContentStatus.POSTPONED);
    }

    @Test
    void rejectsV2WhenTheSiteManagerIsMissing() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(examplePath(topic, 2)));
        ((ObjectNode) root.get("payload")).remove("managerId");

        assertThatThrownBy(() -> decoder.decode(topic, null, objectMapper.writeValueAsString(root)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("managerId");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> decoder.decode(
                EventTopics.CATALOG_SITE_CHANGED, null, "{\"eventType\":"))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void decodesAZoneChangedEvent() {
        UUID zoneId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_ZONE_CHANGED, zoneId, 1, """
                "zoneId": "%s",
                "managerId": "%s",
                "name": "Zona Norte",
                "type": "BEACH",
                "description": "Arenas blancas",
                "status": "CONGESTED",
                "updatedAt": "2026-11-02T15:00:00Z"
                """.formatted(zoneId, UUID.randomUUID()));

        ProjectedEvent event = decoder.decode(
                EventTopics.CATALOG_ZONE_CHANGED, zoneId.toString(), json);

        assertThat(event).isInstanceOf(ZoneChangedEvent.class);
        ZoneChangedEvent zone = (ZoneChangedEvent) event;
        assertThat(zone.zoneId()).isEqualTo(zoneId);
        assertThat(zone.name()).isEqualTo("Zona Norte");
        assertThat(zone.type()).isEqualTo("BEACH");
        assertThat(zone.description()).isEqualTo("Arenas blancas");
        assertThat(zone.status()).isEqualTo(ZoneStatus.CONGESTED);
        assertThat(zone.updatedAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
    }

    @Test
    void acceptsAZoneWithoutOptionalFields() {
        UUID zoneId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_ZONE_CHANGED, zoneId, 1, """
                "zoneId": "%s",
                "managerId": "%s",
                "name": "Zona Centro",
                "status": "ACTIVE",
                "updatedAt": "2026-11-02T15:00:00Z"
                """.formatted(zoneId, UUID.randomUUID()));

        ZoneChangedEvent zone = (ZoneChangedEvent) decoder.decode(
                EventTopics.CATALOG_ZONE_CHANGED, zoneId.toString(), json);

        assertThat(zone.type()).isNull();
        assertThat(zone.description()).isNull();
        assertThat(zone.status()).isEqualTo(ZoneStatus.ACTIVE);
    }

    @Test
    void rejectsAZoneWithAnUnsupportedStatus() {
        UUID zoneId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_ZONE_CHANGED, zoneId, 1, """
                "zoneId": "%s",
                "managerId": "%s",
                "name": "Zona Sur",
                "status": "OPEN",
                "updatedAt": "2026-11-02T15:00:00Z"
                """.formatted(zoneId, UUID.randomUUID()));

        assertThatThrownBy(() -> decoder.decode(
                EventTopics.CATALOG_ZONE_CHANGED, zoneId.toString(), json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("status");
    }

    @Test
    void decodesV2SiteWithItsZone() {
        UUID siteId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 2, """
                "siteId": "%s",
                "name": "Playa Cristal",
                "categoryId": "%s",
                "managerId": "%s",
                "zoneId": "%s",
                "latitude": 11.31,
                "longitude": -74.17,
                "status": "PUBLISHED",
                "updatedAt": "2026-11-02T15:00:00Z"
                """.formatted(siteId, UUID.randomUUID(), UUID.randomUUID(), zoneId));

        SiteChangedEvent site = (SiteChangedEvent) decoder.decode(
                EventTopics.CATALOG_SITE_CHANGED, siteId.toString(), json);

        assertThat(site.zoneId()).isEqualTo(zoneId);
    }

    @Test
    void acceptsV2SiteWithoutZone() {
        UUID siteId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 2, """
                "siteId": "%s",
                "name": "Playa Cristal",
                "categoryId": "%s",
                "managerId": "%s",
                "latitude": 11.31,
                "longitude": -74.17,
                "status": "PUBLISHED",
                "updatedAt": "2026-11-02T15:00:00Z"
                """.formatted(siteId, UUID.randomUUID(), UUID.randomUUID()));

        SiteChangedEvent site = (SiteChangedEvent) decoder.decode(
                EventTopics.CATALOG_SITE_CHANGED, siteId.toString(), json);

        assertThat(site.zoneId()).isNull();
    }

    @Test
    void decodesTheZoneExampleFromContracts() throws Exception {
        String json = Files.readString(examplePath(EventTopics.CATALOG_ZONE_CHANGED));
        var event = decoder.decode(
                EventTopics.CATALOG_ZONE_CHANGED,
                objectMapper.readTree(json).get("aggregateId").asString(),
                json);

        assertThat(event).isInstanceOf(ZoneChangedEvent.class);
        ZoneChangedEvent zone = (ZoneChangedEvent) event;
        assertThat(zone.zoneId()).isEqualTo(UUID.fromString("33333333-3333-3333-3333-333333333301"));
        assertThat(zone.name()).isEqualTo("Centro Histórico");
        assertThat(zone.type()).isEqualTo("URBANA");
        assertThat(zone.status()).isEqualTo(ZoneStatus.ACTIVE);
        assertThat(zone.updatedAt()).isEqualTo(Instant.parse("2026-11-05T04:00:00Z"));
    }

    private String envelope(String eventType, UUID aggregateId, int eventVersion, String payload) {
        return envelope(eventType, aggregateId, eventVersion, "ms-04",
                "2026-11-02T15:00:00Z", payload);
    }

    private String envelope(String eventType, UUID aggregateId, int eventVersion, String producer,
                            String occurredAt, String payload) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": %d,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "test-correlation",
                  "producer": "%s",
                  "payload": {%s}
                }
                """.formatted(UUID.randomUUID(), eventType, eventVersion, occurredAt, aggregateId,
                producer, payload);
    }

    /** MS05-01: an event must be attributed to the producer that owns the topic. */
    @Test
    void rejectsAProducerThatDoesNotMatchTheTopic() {
        UUID siteId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 1, "ms-03",
                "2026-11-02T15:00:00Z", sitePayload(siteId, "2026-11-02T15:00:00Z"));

        assertThatThrownBy(() -> decoder.decode(EventTopics.CATALOG_SITE_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("producer");
    }

    /** MS05-01: a future occurredAt would poison the freshness comparison of the graph. */
    @Test
    void rejectsAnOccurredAtInTheFuture() {
        UUID siteId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 1, "ms-04",
                "2027-01-01T00:10:00Z", sitePayload(siteId, "2026-11-02T15:00:00Z"));

        assertThatThrownBy(() -> decoder.decode(EventTopics.CATALOG_SITE_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("occurredAt");
    }

    /** MS05-01: a future updatedAt would freeze the entity against every later event. */
    @Test
    void rejectsAnUpdatedAtInTheFuture() {
        UUID siteId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 1,
                sitePayload(siteId, "2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> decoder.decode(EventTopics.CATALOG_SITE_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("updatedAt");
    }

    /** The skew tolerance keeps clock drift and in-flight events working. */
    @Test
    void acceptsTimestampsWithinTheConfiguredSkew() {
        UUID siteId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 1, "ms-04",
                "2027-01-01T00:02:00Z", sitePayload(siteId, "2027-01-01T00:02:00Z"));

        ProjectedEvent event = decoder.decode(EventTopics.CATALOG_SITE_CHANGED, null, json);

        assertThat(event).isInstanceOf(SiteChangedEvent.class);
    }

    /** MS05-09: an unbounded payload text must never become a graph property or a reason. */
    @Test
    void rejectsAnOverlongTextInThePayload() {
        UUID siteId = UUID.randomUUID();
        String json = envelope(EventTopics.CATALOG_SITE_CHANGED, siteId, 1, "ms-04",
                "2026-11-02T15:00:00Z",
                sitePayload(siteId, "x".repeat(201), "2026-11-02T15:00:00Z"));

        assertThatThrownBy(() -> decoder.decode(EventTopics.CATALOG_SITE_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("name must be at most 200 characters");
    }

    /** reviews.review.changed has two legitimate publishers: ms-03 and ms-04. */
    @Test
    void acceptsBothLegitimateProducersOfReviews() {
        UUID reviewId = UUID.randomUUID();
        String payload = reviewPayload(reviewId, "2026-11-02T15:00:00Z");

        for (String producer : new String[] {"ms-03-activities-and-events", "ms-04"}) {
            String json = envelope(EventTopics.REVIEWS_REVIEW_CHANGED, reviewId, 1, producer,
                    "2026-11-02T15:00:00Z", payload);

            ProjectedEvent event = decoder.decode(EventTopics.REVIEWS_REVIEW_CHANGED, null, json);

            assertThat(event).isInstanceOf(ReviewChangedEvent.class);
        }
    }

    /** A publisher outside the two legitimate ones is still rejected. */
    @Test
    void rejectsAnUnknownProducerOfReviews() {
        UUID reviewId = UUID.randomUUID();
        String json = envelope(EventTopics.REVIEWS_REVIEW_CHANGED, reviewId, 1, "ms-02",
                "2026-11-02T15:00:00Z", reviewPayload(reviewId, "2026-11-02T15:00:00Z"));

        assertThatThrownBy(() -> decoder.decode(EventTopics.REVIEWS_REVIEW_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("producer");
    }

    private String reviewPayload(UUID reviewId, String updatedAt) {
        return """
                "reviewId": "%s",
                "touristId": "%s",
                "itemType": "ACTIVITY",
                "itemId": "%s",
                "rating": 5,
                "status": "PUBLISHED",
                "updatedAt": "%s"
                """.formatted(reviewId, UUID.randomUUID(), UUID.randomUUID(), updatedAt);
    }

    private String sitePayload(UUID siteId, String updatedAt) {
        return sitePayload(siteId, "Playa Cristal", updatedAt);
    }

    private String sitePayload(UUID siteId, String name, String updatedAt) {
        return """
                "siteId": "%s",
                "name": "%s",
                "categoryId": "%s",
                "latitude": 11.31,
                "longitude": -74.17,
                "status": "PUBLISHED",
                "updatedAt": "%s"
                """.formatted(siteId, name, UUID.randomUUID(), updatedAt);
    }

    private Path examplePath(String topic) {
        return contractsDirectory.resolve("examples").resolve(topic + ".v1.example.json");
    }

    private Path examplePath(String topic, int version) {
        return contractsDirectory.resolve("examples").resolve(topic + ".v" + version + ".example.json");
    }
}
