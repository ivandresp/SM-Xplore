package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.CategoryScope;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.catalog.ZoneStatus;
import com.smxplore.recommendations.domain.model.event.ActivityChangedEvent;
import com.smxplore.recommendations.domain.model.event.CategoryChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventMetadata;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ReviewChangedEvent;
import com.smxplore.recommendations.domain.model.event.ReviewStatus;
import com.smxplore.recommendations.domain.model.event.ServiceChangedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.model.event.ZoneChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectionEventDecoder {

    /**
     * Maximum length of the free text fields persisted in the graph (MS05-09). A producer can
     * publish a syntactically valid payload with an arbitrarily long string; without this bound
     * those values inflate node properties, the cache and the recommendation reasons.
     */
    private static final Map<String, Integer> MAX_TEXT_LENGTHS = Map.of(
            "eventType", 60,
            "producer", 100,
            "correlationId", 100,
            "name", 200,
            "description", 2000,
            "type", 50,
            "category", 200,
            "status", 50,
            "language", 35
    );

    private final ObjectMapper objectMapper;
    private final EventConsumerProperties properties;
    private final Clock clock;

    public ProjectedEvent decode(String topic, String key, String json) {
        JsonNode root = parse(json);
        JsonNode payload = requiredObject(root);

        EventMetadata metadata = new EventMetadata(
                requiredUuid(root, "eventId"),
                requiredText(root, "eventType"),
                requiredInt(root, "eventVersion"),
                requiredInstant(root, "occurredAt"),
                requiredUuid(root, "aggregateId"),
                requiredText(root, "correlationId"),
                requiredText(root, "producer")
        );

        if (!metadata.eventType().equals(topic)) {
            throw new InvalidEventException(
                    "eventType " + metadata.eventType() + " does not match topic " + topic);
        }
        if (!EventTopics.isConsumed(topic)) {
            throw new InvalidEventException("Topic is not consumed by ms-05: " + topic);
        }
        if (key != null && !key.equals(metadata.aggregateId().toString())) {
            throw new InvalidEventException("Kafka key must be the aggregateId");
        }

        validateProducer(metadata);

        ProjectedEvent event = switch (topic) {
            case EventTopics.CATALOG_CATEGORY_CHANGED -> decodeCategory(metadata, payload);
            case EventTopics.CATALOG_ZONE_CHANGED -> decodeZone(metadata, payload);
            case EventTopics.CATALOG_SITE_CHANGED -> decodeSite(metadata, payload);
            case EventTopics.CATALOG_SERVICE_CHANGED -> decodeService(metadata, payload);
            case EventTopics.ACTIVITIES_ACTIVITY_CHANGED -> decodeActivity(metadata, payload);
            case EventTopics.ACTIVITIES_EVENT_CHANGED -> decodeEventItem(metadata, payload);
            case EventTopics.REVIEWS_REVIEW_CHANGED -> decodeReview(metadata, payload);
            default -> throw new InvalidEventException("Unsupported eventType: " + metadata.eventType());
        };
        validateTimestamps(event);
        return event;
    }

    /**
     * Rejects events attributed to a producer that does not publish the topic (MS05-01).
     * reviews.review.changed has two legitimate publishers (ms-03 for activities/events and
     * ms-04 for sites/services), so the check accepts the whole set. The field is
     * self-declared, so this only catches publisher mistakes and first-line forgery; the
     * broker still has to add authentication and ACLs.
     */
    private void validateProducer(EventMetadata metadata) {
        Set<String> expected = EventTopics.expectedProducers(metadata.eventType());
        if (!expected.isEmpty() && !expected.contains(metadata.producer())) {
            throw new InvalidEventException(
                    "producer " + metadata.producer() + " is not an expected producer "
                            + expected + " for " + metadata.eventType());
        }
    }

    /**
     * Rejects events whose {@code occurredAt} or {@code updatedAt} is further in the future
     * than the configured skew (MS05-01). A future timestamp would make the freshness query
     * discard every legitimate later event, freezing the projected entity.
     */
    private void validateTimestamps(ProjectedEvent event) {
        Instant limit = clock.instant().plus(properties.getMaxFutureSkew());
        if (event.metadata().occurredAt().isAfter(limit)) {
            throw new InvalidEventException("occurredAt must not be in the future");
        }
        if (event.updatedAt().isAfter(limit)) {
            throw new InvalidEventException("updatedAt must not be in the future");
        }
    }

    private ProjectedEvent decodeCategory(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1);
        UUID categoryId = requiredUuid(payload, "categoryId");
        requireAggregate(metadata, categoryId, "categoryId");
        return new CategoryChangedEvent(
                metadata,
                categoryId,
                requiredText(payload, "name"),
                requiredEnum(payload, "scope", CategoryScope.class),
                optionalUuid(payload, "parentId"),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeZone(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1);
        UUID zoneId = requiredUuid(payload, "zoneId");
        requireAggregate(metadata, zoneId, "zoneId");
        return new ZoneChangedEvent(
                metadata,
                zoneId,
                requiredUuid(payload, "managerId"),
                requiredText(payload, "name"),
                optionalText(payload, "type"),
                optionalText(payload, "description"),
                requiredEnum(payload, "status", ZoneStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeSite(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1, 2);
        UUID siteId = requiredUuid(payload, "siteId");
        requireAggregate(metadata, siteId, "siteId");
        if (metadata.eventVersion() == 2) {
            // v2 requires the manager so ms-03 can refresh the snapshot it keeps on events.
            requiredUuid(payload, "managerId");
        }
        return new SiteChangedEvent(
                metadata,
                siteId,
                requiredText(payload, "name"),
                requiredUuid(payload, "categoryId"),
                optionalUuid(payload, "zoneId"),
                requiredDouble(payload, "latitude"),
                requiredDouble(payload, "longitude"),
                requiredEnum(payload, "status", ContentStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeService(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1);
        UUID serviceId = requiredUuid(payload, "serviceId");
        requireAggregate(metadata, serviceId, "serviceId");
        return new ServiceChangedEvent(
                metadata,
                serviceId,
                requiredUuid(payload, "siteId"),
                requiredText(payload, "name"),
                requiredText(payload, "type"),
                requiredUuid(payload, "categoryId"),
                requiredDouble(payload, "latitude"),
                requiredDouble(payload, "longitude"),
                requiredEnum(payload, "status", ContentStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeActivity(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1, 2);
        UUID activityId = requiredUuid(payload, "activityId");
        requireAggregate(metadata, activityId, "activityId");
        if (metadata.eventVersion() == 1) {
            return new ActivityChangedEvent(
                    metadata,
                    activityId,
                    requiredText(payload, "name"),
                    requiredUuid(payload, "categoryId"),
                    requiredUuid(payload, "siteId"),
                    requiredDouble(payload, "latitude"),
                    requiredDouble(payload, "longitude"),
                    requiredDouble(payload, "price"),
                    requiredEnum(payload, "status", ContentStatus.class),
                    requiredInstant(payload, "updatedAt")
            );
        }
        String category = requiredText(payload, "category");
        return new ActivityChangedEvent(
                metadata,
                activityId,
                requiredText(payload, "name"),
                deterministicCategoryId(category),
                category,
                requiredUuid(payload, "siteId"),
                null,
                null,
                requiredDouble(payload, "price"),
                requiredEnum(payload, "status", ContentStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeEventItem(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1, 2);
        UUID itemId = requiredUuid(payload, "eventId");
        requireAggregate(metadata, itemId, "eventId");
        if (metadata.eventVersion() == 1) {
            return new EventItemChangedEvent(
                    metadata,
                    itemId,
                    requiredText(payload, "name"),
                    requiredUuid(payload, "categoryId"),
                    requiredUuid(payload, "siteId"),
                    requiredDouble(payload, "latitude"),
                    requiredDouble(payload, "longitude"),
                    requiredDouble(payload, "price"),
                    requiredInstant(payload, "startsAt"),
                    optionalInstant(payload),
                    requiredEnum(payload, "status", ContentStatus.class),
                    requiredInstant(payload, "updatedAt")
            );
        }
        String category = requiredText(payload, "category");
        return new EventItemChangedEvent(
                metadata,
                itemId,
                requiredText(payload, "name"),
                deterministicCategoryId(category),
                category,
                requiredUuid(payload, "siteId"),
                null,
                null,
                requiredDouble(payload, "price"),
                requiredInstant(payload, "startsAt"),
                optionalInstant(payload),
                requiredEnum(payload, "status", ContentStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeReview(EventMetadata metadata, JsonNode payload) {
        requireVersion(metadata, 1);
        UUID reviewId = requiredUuid(payload, "reviewId");
        requireAggregate(metadata, reviewId, "reviewId");
        return new ReviewChangedEvent(
                metadata,
                reviewId,
                requiredUuid(payload, "touristId"),
                requiredEnum(payload, "itemType", ItemType.class),
                requiredUuid(payload, "itemId"),
                requiredInt(payload, "rating"),
                requiredEnum(payload, "status", ReviewStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private static UUID deterministicCategoryId(String category) {
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(
                ("ms-03-category:" + normalized).getBytes(StandardCharsets.UTF_8));
    }

    private static void requireVersion(EventMetadata metadata, int... supported) {
        for (int version : supported) {
            if (metadata.eventVersion() == version) {
                return;
            }
        }
        throw new InvalidEventException(
                "Unsupported eventVersion " + metadata.eventVersion() + " for " + metadata.eventType());
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidEventException("Kafka event payload is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new InvalidEventException("Kafka event payload must be a JSON object");
            }
            return root;
        } catch (JacksonException exception) {
            throw new InvalidEventException("Kafka event payload is not valid JSON", exception);
        }
    }

    private void requireAggregate(EventMetadata metadata, UUID payloadId, String field) {
        if (!metadata.aggregateId().equals(payloadId)) {
            throw new InvalidEventException("aggregateId must match payload." + field);
        }
    }

    private JsonNode requiredObject(JsonNode parent) {
        JsonNode node = parent.get("payload");
        if (node == null || !node.isObject()) {
            throw new InvalidEventException("payload must be an object");
        }
        return node;
    }

    private String requiredText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new InvalidEventException(field + " is required");
        }
        String value = node.asString();
        checkTextLength(field, value);
        return value;
    }

    private UUID requiredUuid(JsonNode parent, String field) {
        String value = requiredText(parent, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException(field + " must be a UUID", exception);
        }
    }

    private UUID optionalUuid(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidEventException(field + " must be a UUID or null");
        }
        try {
            return UUID.fromString(node.asString());
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException(field + " must be a UUID", exception);
        }
    }

    private String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidEventException(field + " must be a string or null");
        }
        String value = node.asString();
        if (value.isBlank()) {
            return null;
        }
        checkTextLength(field, value);
        return value;
    }

    private static void checkTextLength(String field, String value) {
        Integer maxLength = MAX_TEXT_LENGTHS.get(field);
        if (maxLength != null && value.length() > maxLength) {
            throw new InvalidEventException(field + " must be at most " + maxLength + " characters");
        }
    }

    private int requiredInt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new InvalidEventException(field + " must be an integer");
        }
        return node.asInt();
    }

    private double requiredDouble(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isNumber()) {
            throw new InvalidEventException(field + " must be a number");
        }
        return node.asDouble();
    }

    private Instant requiredInstant(JsonNode parent, String field) {
        String value = requiredText(parent, field);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidEventException(field + " must be an ISO-8601 instant", exception);
        }
    }

    private Instant optionalInstant(JsonNode parent) {
        JsonNode node = parent.get("endsAt");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidEventException("endsAt must be an ISO-8601 instant or null");
        }
        try {
            return Instant.parse(node.asString());
        } catch (DateTimeParseException exception) {
            throw new InvalidEventException("endsAt must be an ISO-8601 instant", exception);
        }
    }

    private <E extends Enum<E>> E requiredEnum(JsonNode parent, String field, Class<E> enumType) {
        String value = requiredText(parent, field);
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException(field + " has an unsupported value: " + value, exception);
        }
    }
}
