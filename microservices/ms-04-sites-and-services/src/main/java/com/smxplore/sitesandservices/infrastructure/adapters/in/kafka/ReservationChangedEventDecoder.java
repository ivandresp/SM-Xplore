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
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationChangedEventDecoder {

    public static final String EVENT_TYPE = "bookings.reservation.changed";

    private final ObjectMapper objectMapper;

    public Optional<ReservationChangedMessage> decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!EVENT_TYPE.equals(root.path("eventType").asString())) {
                return Optional.empty();
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
            List<ReservationChangedMessage.Item> items = new ArrayList<>();
            for (JsonNode itemNode : itemsNode.values()) {
                String itemType = requiredText(itemNode, "itemType");
                UUID itemId = requiredUuid(itemNode, "itemId");
                UUID providerId = optionalUuid(itemNode, "providerId");
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
                        providerId,
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
