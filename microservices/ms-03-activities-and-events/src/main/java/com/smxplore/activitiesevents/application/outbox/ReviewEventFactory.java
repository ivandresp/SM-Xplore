package com.smxplore.activitiesevents.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.model.review.Review;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Builds the v1 reviews.review.changed envelope from a review, following
 * contracts/events/reviews.review.changed.v1.json.
 *
 * <p>The payload carries no free-text comment on purpose: consumers get the rating, the status and
 * the identity of the reviewed item, never the user's text.
 *
 * <p>El {@code eventId} es determinista (uuid v3 sobre reseña + {@code updatedAt}) para que un
 * re-despacho de la conciliación produzca exactamente el mismo evento: el outbox lo descarta por
 * clave primaria y el consumidor vuelve a ver un evento idempotente en lugar de un duplicado.
 */
@Component
@RequiredArgsConstructor
public class ReviewEventFactory {

    private static final String EVENT_TYPE = "reviews.review.changed";
    private static final int EVENT_VERSION = 1;
    private static final String PRODUCER = "ms-03-activities-and-events";

    private final ObjectMapper objectMapper;

    public Mono<OutboxEvent> reviewChanged(Review review) {
        var now = Instant.now();
        var eventId = eventId(review);
        var correlationId = UUID.randomUUID();

        var payload = new LinkedHashMap<String, Object>();
        payload.put("reviewId", review.getId());
        payload.put("touristId", review.getUser().id());
        payload.put("itemType", review.getItemType().name());
        payload.put("itemId", review.getItemId());
        payload.put("rating", review.getRating());
        payload.put("status", review.getStatus().name());
        payload.put("updatedAt", review.updatedAt().toString());

        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", now.toString());
        envelope.put("aggregateId", review.getId());
        envelope.put("correlationId", correlationId);
        envelope.put("producer", PRODUCER);
        envelope.put("payload", payload);

        try {
            var json = objectMapper.writeValueAsString(envelope);
            return Mono.just(new OutboxEvent(
                    eventId, EVENT_TYPE, EVENT_VERSION, review.getId(),
                    correlationId, json, now));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    /**
     * Identidad del evento: mismo par (reseña, {@code updatedAt}) produce siempre el mismo uuid, de
     * modo que la conciliación puede re-despachar sin duplicar. {@code updatedAt} cambia con cada
     * cambio real de la reseña, así que dos cambios distintos sí generan eventos distintos.
     */
    static UUID eventId(Review review) {
        var seed = EVENT_TYPE + ":" + review.getId() + ":" + review.updatedAt();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
