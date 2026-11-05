package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.CategoryScope;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectionEventDecoder {

    private final ObjectMapper objectMapper;

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

        return switch (topic) {
            case EventTopics.CATALOG_CATEGORY_CHANGED -> decodeCategory(metadata, payload);
            case EventTopics.CATALOG_SITE_CHANGED -> decodeSite(metadata, payload);
            case EventTopics.CATALOG_SERVICE_CHANGED -> decodeService(metadata, payload);
            case EventTopics.ACTIVITIES_ACTIVITY_CHANGED -> decodeActivity(metadata, payload);
            case EventTopics.ACTIVITIES_EVENT_CHANGED -> decodeEventItem(metadata, payload);
            case EventTopics.REVIEWS_REVIEW_CHANGED -> decodeReview(metadata, payload);
            default -> throw new InvalidEventException("Unsupported eventType: " + metadata.eventType());
        };
    }

    private ProjectedEvent decodeCategory(EventMetadata metadata, JsonNode payload) {
        UUID categoryId = requiredUuid(payload, "categoryId");
        requireAggregate(metadata, categoryId, "categoryId");
        return new CategoryChangedEvent(
                metadata,
                categoryId,
                requiredText(payload, "name"),
                requiredEnum(payload, "scope", CategoryScope.class),
                optionalUuid(payload),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeSite(EventMetadata metadata, JsonNode payload) {
        UUID siteId = requiredUuid(payload, "siteId");
        requireAggregate(metadata, siteId, "siteId");
        return new SiteChangedEvent(
                metadata,
                siteId,
                requiredText(payload, "name"),
                requiredUuid(payload, "categoryId"),
                requiredDouble(payload, "latitude"),
                requiredDouble(payload, "longitude"),
                requiredEnum(payload, "status", ContentStatus.class),
                requiredInstant(payload, "updatedAt")
        );
    }

    private ProjectedEvent decodeService(EventMetadata metadata, JsonNode payload) {
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
        UUID activityId = requiredUuid(payload, "activityId");
        requireAggregate(metadata, activityId, "activityId");
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

    private ProjectedEvent decodeEventItem(EventMetadata metadata, JsonNode payload) {
        UUID itemId = requiredUuid(payload, "eventId");
        requireAggregate(metadata, itemId, "eventId");
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

    private ProjectedEvent decodeReview(EventMetadata metadata, JsonNode payload) {
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
        return node.asString();
    }

    private UUID requiredUuid(JsonNode parent, String field) {
        String value = requiredText(parent, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException(field + " must be a UUID", exception);
        }
    }

    private UUID optionalUuid(JsonNode parent) {
        JsonNode node = parent.get("parentId");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidEventException("parentId must be a UUID or null");
        }
        try {
            return UUID.fromString(node.asString());
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException("parentId must be a UUID", exception);
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
