package com.smxplore.activitiesevents.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the v2 activities.event.changed envelope from an event, following
 * contracts/events/activities.event.changed.v2.json.
 *
 * <p>v2 replaces the v1 {@code categoryId} with the free-text {@code category} ms-03 really stores,
 * drops the coordinates that belong to the site in ms-04 and allows {@code POSTPONED}.
 *
 * <p>{@link #payload(Event, Instant)} es público porque el feed de exportación del catálogo
 * (P0-1) debe entregar exactamente el mismo payload que el evento vivo: si el contrato cambia, se
 * cambia en un único sitio.
 */
@Component
@RequiredArgsConstructor
public class EventChangedEventFactory {

    public static final String EVENT_TYPE = "activities.event.changed";
    public static final int EVENT_VERSION = 2;
    private static final String PRODUCER = "ms-03-activities-and-events";

    private final ObjectMapper objectMapper;

    public Mono<OutboxEvent> eventChanged(Event event) {
        var now = Instant.now();
        var eventId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();
        return envelope(eventId, event.getId(), correlationId, payload(event, now), now);
    }

    /**
     * Payload v2 del agregado. {@code updatedAt} se inyecta para que el feed de exportación use la
     * marca persistida ({@code updated_at}) y el evento vivo use el instante de ocurrencia.
     */
    public Map<String, Object> payload(Event event, Instant updatedAt) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("eventId", event.getId());
        payload.put("siteId", event.getSiteId());
        payload.put("managerId", event.getManagerId());
        payload.put("siteName", event.getSiteName());
        payload.put("name", event.getName());
        payload.put("category", event.getCategory());
        payload.put("price", event.getPrice());
        payload.put("status", event.getStatus().name());
        payload.put("maxCapacity", event.getMaxCapacity());
        payload.put("startsAt", text(event.getStartsAt()));
        payload.put("endsAt", text(event.getEndsAt()));
        payload.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        return payload;
    }

    private Mono<OutboxEvent> envelope(UUID eventId, UUID aggregateId, UUID correlationId,
                                       Map<String, Object> payload, Instant now) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", now.toString());
        envelope.put("aggregateId", aggregateId);
        envelope.put("correlationId", correlationId);
        envelope.put("producer", PRODUCER);
        envelope.put("payload", payload);
        try {
            var json = objectMapper.writeValueAsString(envelope);
            return Mono.just(new OutboxEvent(eventId, EVENT_TYPE, EVENT_VERSION, aggregateId,
                    correlationId, json, now));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
