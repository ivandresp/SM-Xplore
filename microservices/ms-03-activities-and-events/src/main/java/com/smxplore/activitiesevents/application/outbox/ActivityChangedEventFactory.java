package com.smxplore.activitiesevents.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Builds the v2 activities.activity.changed envelope from an activity, following
 * contracts/events/activities.activity.changed.v2.json.
 *
 * <p>v2 replaces the v1 {@code categoryId} with the free-text {@code category} ms-03 really stores,
 * drops the coordinates that belong to the site in ms-04 and allows the four real statuses.
 */
@Component
@RequiredArgsConstructor
public class ActivityChangedEventFactory {

    private static final String EVENT_TYPE = "activities.activity.changed";
    private static final int EVENT_VERSION = 2;
    private static final String PRODUCER = "ms-03-activities-and-events";

    private final ObjectMapper objectMapper;

    public Mono<OutboxEvent> activityChanged(Activity activity) {
        var now = Instant.now();
        var eventId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();

        var payload = new LinkedHashMap<String, Object>();
        payload.put("activityId", activity.getId());
        payload.put("siteId", activity.getSiteId());
        payload.put("guideId", activity.getGuideId());
        payload.put("name", activity.getName());
        payload.put("category", activity.getCategory());
        payload.put("price", activity.getPrice());
        payload.put("status", activity.getStatus().name());
        payload.put("maxCapacity", activity.getMaxCapacity());
        payload.put("durationMinutes", activity.getDurationMinutes());
        payload.put("startsAt", text(activity.getStartsAt()));
        payload.put("endsAt", text(activity.getEndsAt()));
        payload.put("updatedAt", now.toString());

        return envelope(eventId, activity.getId(), correlationId, payload, now);
    }

    private Mono<OutboxEvent> envelope(UUID eventId, UUID aggregateId, UUID correlationId,
                                       LinkedHashMap<String, Object> payload, Instant now) {
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
