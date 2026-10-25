package com.smxplore.activitiesevents.domain.model.event;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventNameException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventPriceException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidExpectedAttendanceException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTest {

    private Event validEvent() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        return Event.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .name("Sunset Kayak Tour")
                .category("Nautical")
                .price(new BigDecimal("150000.00"))
                .maxCapacity(12)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7200))
                .description("Guided kayak tour at sunset.")
                .build();
    }

    @Test
    void shouldCreateValidEventWithDefaults() {
        Event event = validEvent();

        event.validate();

        assertEquals(EventStatus.DRAFT, event.getStatus());
        assertEquals(0, event.getExpectedAttendance());
    }

    @Test
    void shouldThrowWhenSiteIdIsNull() {
        Event event = validEvent().toBuilder().siteId(null).build();

        assertThrows(InvalidEventRequestException.class, event::validate);
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        Event event = validEvent().toBuilder().name("  ").build();

        assertThrows(InvalidEventNameException.class, event::validate);
    }

    @Test
    void shouldThrowWhenNameIsTooLong() {
        Event event = validEvent().toBuilder().name("x".repeat(151)).build();

        assertThrows(InvalidEventNameException.class, event::validate);
    }

    @Test
    void shouldThrowWhenCategoryIsTooLong() {
        Event event = validEvent().toBuilder().category("c".repeat(51)).build();

        assertThrows(InvalidEventRequestException.class, event::validate);
    }

    @Test
    void shouldThrowWhenPriceIsNull() {
        Event event = validEvent().toBuilder().price(null).build();

        assertThrows(InvalidEventPriceException.class, event::validate);
    }

    @Test
    void shouldThrowWhenPriceIsNegative() {
        Event event = validEvent().toBuilder().price(new BigDecimal("-1.00")).build();

        assertThrows(InvalidEventPriceException.class, event::validate);
    }

    @Test
    void shouldThrowWhenCapacityIsNotPositive() {
        Event event = validEvent().toBuilder().maxCapacity(0).build();

        assertThrows(InvalidEventCapacityException.class, event::validate);
    }

    @Test
    void shouldThrowWhenScheduleIsInverted() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Event event = validEvent().toBuilder()
                .startsAt(startsAt)
                .endsAt(startsAt.minusSeconds(60))
                .build();

        assertThrows(InvalidEventScheduleException.class, event::validate);
    }

    @Test
    void shouldThrowWhenDatesAreNull() {
        Event event = validEvent().toBuilder().startsAt(null).build();

        assertThrows(InvalidEventScheduleException.class, event::validate);
    }

    @Test
    void shouldPublishDraftEvent() {
        Event event = validEvent();

        event.changeStatus(EventStatus.PUBLISHED);

        assertEquals(EventStatus.PUBLISHED, event.getStatus());
    }

    @Test
    void shouldThrowWhenPublishingCancelledEvent() {
        Event event = validEvent().toBuilder().status(EventStatus.CANCELLED).build();

        assertThrows(InvalidEventStatusTransitionException.class, () -> event.changeStatus(EventStatus.PUBLISHED));
    }

    @Test
    void shouldThrowDomainExceptionWhenChangingStatusToNull() {
        Event event = validEvent();

        assertThrows(InvalidEventStatusTransitionException.class, () -> event.changeStatus(null));
    }

    @Test
    void shouldSetExpectedAttendance() {
        Event event = validEvent();

        event.estimateAttendance(10);

        assertEquals(10, event.getExpectedAttendance());
    }

    @Test
    void shouldRejectAttendanceAboveCapacity() {
        Event event = validEvent();

        assertThrows(InvalidExpectedAttendanceException.class, () -> event.estimateAttendance(13));
    }

    @Test
    void shouldThrowWhenAttendanceIsNegative() {
        Event event = validEvent();

        assertThrows(InvalidExpectedAttendanceException.class, () -> event.estimateAttendance(-1));
    }
}
