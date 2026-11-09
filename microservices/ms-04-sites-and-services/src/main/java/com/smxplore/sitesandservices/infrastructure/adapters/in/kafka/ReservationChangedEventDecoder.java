package com.smxplore.sitesandservices.infrastructure.adapters.in.kafka;

import com.smxplore.sitesandservices.application.notification.ReservationChangedMessage;
import com.smxplore.sitesandservices.domain.exception.InvalidNotificationEventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationChangedEventDecoder {

    public static final String EVENT_TYPE = "bookings.reservation.changed";

    /** MS04-11: solo se aceptan eventos de productores conocidos y versiones soportadas. */
    private static final Set<String> ALLOWED_PRODUCERS = Set.of(
            "ms-03",
            "ms-03-activities-and-events",
            "ms-04"
    );
    private static final int MAX_ITEMS = 50;

    private final ObjectMapper objectMapper;

    public Optional<ReservationChangedMessage> decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!EVENT_TYPE.equals(root.path("eventType").asString())) {
                return Optional.empty();
            }
            String producer = root.path("producer").asStringOpt().orElse(null);
            if (producer == null || producer.isBlank()) {
                throw new InvalidNotificationEventException(
                        "Booking event producer is required");
            }
            if (!ALLOWED_PRODUCERS.contains(producer)) {
                throw new InvalidNotificationEventException(
                        "Unexpected booking event producer: " + producer);
            }
            if (root.hasNonNull("eventVersion")
                    && !isSupportedVersion(root.path("eventVersion").asInt(-1))) {
                throw new InvalidNotificationEventException(
                        "Unsupported booking event version: "
                                + root.path("eventVersion").asString());
            }
            JsonNode payload = root.path("payload");
            UUID eventId = requiredUuid(root, "eventId");
            UUID reservationId = requiredUuid(payload, "reservationId");
            UUID touristId = requiredUuid(payload, "touristId");
            Instant bookedFor = optionalInstant(payload, "bookedFor");
            String status = requiredText(payload, "status");
            JsonNode itemsNode = payload.path("items");
            if (!itemsNode.isArray()) {
                throw new InvalidNotificationEventException(
                        "Booking event items must be an array");
            }
            if (itemsNode.size() > MAX_ITEMS) {
                throw new InvalidNotificationEventException(
                        "Booking event must not contain more than " + MAX_ITEMS + " items");
            }
            List<ReservationChangedMessage.Item> items = new ArrayList<>();
            for (JsonNode itemNode : itemsNode.values()) {
                String itemType = requiredText(itemNode, "itemType");
                if (!"SERVICE".equals(itemType) && !"ACTIVITY".equals(itemType)
                        && !"EVENT".equals(itemType)) {
                    throw new InvalidNotificationEventException(
                            "Unsupported booking itemType: " + itemType);
                }
                UUID itemId = requiredUuid(itemNode, "itemId");
                UUID providerId = optionalUuid(itemNode, "providerId");
                UUID guideId = optionalUuid(itemNode, "guideId");
                UUID managerId = optionalUuid(itemNode, "managerId");
                String itemName = itemNode.path("itemName").asStringOpt().orElse(null);
                String serviceName = itemNode.path("serviceName").asStringOpt().orElse(null);
                int people = itemNode.path("people").asInt(0);
                if (people < 1) {
                    throw new InvalidNotificationEventException(
                            "Booking event item people must be >= 1");
                }
                BigDecimal total = itemNode.hasNonNull("total")
                        ? new BigDecimal(itemNode.path("total").asString())
                        : BigDecimal.ZERO;
                items.add(new ReservationChangedMessage.Item(
                        itemType,
                        itemId,
                        itemName,
                        providerId,
                        guideId,
                        managerId,
                        serviceName,
                        people,
                        total
                ));
            }
            if (items.isEmpty()) {
                throw new InvalidNotificationEventException(
                        "Booking event must contain at least one item");
            }
            return Optional.of(new ReservationChangedMessage(
                    eventId,
                    reservationId,
                    touristId,
                    bookedFor,
                    status,
                    items
            ));
        } catch (JacksonException exception) {
            throw new InvalidNotificationEventException(
                    "Could not parse booking event", exception);
        } catch (IllegalArgumentException exception) {
            throw new InvalidNotificationEventException(
                    "Invalid booking event", exception);
        }
    }

    private UUID requiredUuid(JsonNode node, String field) {
        return UUID.fromString(requiredText(node, field));
    }

    private static boolean isSupportedVersion(int version) {
        return version == 1 || version == 2;
    }

    private UUID optionalUuid(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        return UUID.fromString(node.path(field).asString());
    }

    private Instant optionalInstant(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        return Instant.parse(node.path(field).asString());
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asStringOpt().orElse(null);
        if (value == null || value.isBlank()) {
            throw new InvalidNotificationEventException(
                    "Booking event field is required: " + field);
        }
        return value;
    }
}
