package com.smxplore.activitiesevents.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Builds the v2 bookings.reservation.changed envelope from a reservation. Version 2 adds
 * {@code itemName}, explicit {@code guideId}/{@code managerId} and the {@code NO_SHOW} status so
 * ms-04 can notify tourists, guides and destination managers.
 */
@Component
@RequiredArgsConstructor
public class ReservationEventFactory {

    private static final String EVENT_TYPE = "bookings.reservation.changed";
    private static final int EVENT_VERSION = 2;
    private static final String PRODUCER = "ms-03-activities-and-events";

    private final ObjectMapper objectMapper;

    public Mono<OutboxEvent> reservationChanged(Reservation reservation) {
        var status = contractStatus(reservation.getStatus());
        if (status == null) {
            return Mono.empty();
        }
        var now = Instant.now();
        var eventId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();

        var item = new LinkedHashMap<String, Object>();
        item.put("itemType", reservation.getItemType().name());
        item.put("itemId", reservation.getItemId());
        item.put("itemName", itemName(reservation));
        if (reservation.getGuideId() != null) {
            item.put("guideId", reservation.getGuideId());
        }
        if (reservation.getItemType() == ReservationItemType.EVENT && reservation.getProviderId() != null) {
            item.put("managerId", reservation.getProviderId());
        }
        item.put("people", reservation.getNumberOfPeople());
        item.put("total", reservation.getTotal());

        var payload = new LinkedHashMap<String, Object>();
        payload.put("reservationId", reservation.getId());
        payload.put("touristId", reservation.getTouristId());
        if (reservation.getBookedFor() != null) {
            payload.put("bookedFor", reservation.getBookedFor().toString());
        }
        payload.put("status", status);
        payload.put("items", List.of(item));
        payload.put("updatedAt", now.toString());

        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", now.toString());
        envelope.put("aggregateId", reservation.getId());
        envelope.put("correlationId", correlationId);
        envelope.put("producer", PRODUCER);
        envelope.put("payload", payload);

        try {
            var json = objectMapper.writeValueAsString(envelope);
            return Mono.just(new OutboxEvent(
                    eventId, EVENT_TYPE, EVENT_VERSION, reservation.getId(),
                    correlationId, json, now));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    private static String itemName(Reservation reservation) {
        if (reservation.getItemName() != null && !reservation.getItemName().isBlank()) {
            return reservation.getItemName();
        }
        return reservation.getItemType() == ReservationItemType.EVENT
                ? "Evento reservado"
                : "Actividad reservada";
    }

    private static String contractStatus(ReservationStatus status) {
        return switch (status) {
            case CONFIRMED -> "CONFIRMED";
            case CANCELLED -> "CANCELLED";
            case COMPLETED -> "COMPLETED";
            case NO_SHOW -> "NO_SHOW";
        };
    }
}
