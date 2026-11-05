package com.smxplore.activitiesevents.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationEventFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReservationEventFactory factory = new ReservationEventFactory(objectMapper);

    @Test
    void buildsTheBookingsEnvelopeForAVisibleStatus() throws Exception {
        var reservation = reservation(ReservationStatus.CONFIRMED);

        var event = factory.reservationChanged(reservation).block();

        assertThat(event).isNotNull();
        assertThat(event.eventType()).isEqualTo("bookings.reservation.changed");
        assertThat(event.eventVersion()).isEqualTo(1);

        var envelope = objectMapper.readTree(event.payload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(event.id().toString());
        assertThat(envelope.get("correlationId").asText()).isEqualTo(event.correlationId().toString());
        assertThat(envelope.get("producer").asText()).isEqualTo("ms-03-activities-and-events");
        assertThat(envelope.at("/payload/reservationId").asText()).isEqualTo(reservation.getId().toString());
        assertThat(envelope.at("/payload/status").asText()).isEqualTo("CONFIRMED");
        assertThat(envelope.at("/payload/items/0/itemType").asText()).isEqualTo("ACTIVITY");
        assertThat(envelope.at("/payload/items/0/people").asInt()).isEqualTo(2);
        assertThat(envelope.at("/payload/items/0/total").decimalValue()).isEqualByComparingTo("200.00");
    }

    @Test
    void doesNotPublishNoShowUntilContractV2() {
        var reservation = reservation(ReservationStatus.NO_SHOW);

        assertThat(factory.reservationChanged(reservation).block()).isNull();
    }

    private static Reservation reservation(ReservationStatus status) {
        return Reservation.builder()
                .id(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(UUID.randomUUID())
                .numberOfPeople(2)
                .total(new BigDecimal("200.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(status)
                .bookedFor(Instant.now().plusSeconds(3600))
                .build();
    }
}
