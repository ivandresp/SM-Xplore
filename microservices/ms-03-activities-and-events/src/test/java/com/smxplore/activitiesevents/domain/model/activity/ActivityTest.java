package com.smxplore.activitiesevents.domain.model.activity;

import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityNameException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityPriceException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityStatusTransitionException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityTest {

    private Activity validActivity() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        return Activity.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .guideId(UUID.randomUUID())
                .name("Snorkel at Playa Cristal")
                .category("Water Sports")
                .price(new BigDecimal("120000.00"))
                .maxCapacity(10)
                .durationMinutes(120)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7200))
                .description("Guided snorkeling activity.")
                .build();
    }

    @Test
    void shouldCreateValidActivityWithDefaults() {
        Activity activity = validActivity();

        activity.validate();

        assertEquals(ActivityStatus.DRAFT, activity.getStatus());
    }

    @Test
    void shouldThrowWhenSiteIdIsNull() {
        Activity activity = validActivity().toBuilder().siteId(null).build();

        assertThrows(InvalidActivityRequestException.class, activity::validate);
    }

    @Test
    void shouldCreateActivityWithoutGuide() {
        Activity activity = validActivity().toBuilder().guideId(null).build();

        activity.validate();

        assertNull(activity.getGuideId());
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        Activity activity = validActivity().toBuilder().name("  ").build();

        assertThrows(InvalidActivityNameException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenNameIsTooLong() {
        Activity activity = validActivity().toBuilder().name("x".repeat(151)).build();

        assertThrows(InvalidActivityNameException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenCategoryIsTooLong() {
        Activity activity = validActivity().toBuilder().category("c".repeat(51)).build();

        assertThrows(InvalidActivityRequestException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenPriceIsNull() {
        Activity activity = validActivity().toBuilder().price(null).build();

        assertThrows(InvalidActivityPriceException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenPriceIsNegative() {
        Activity activity = validActivity().toBuilder().price(new BigDecimal("-1.00")).build();

        assertThrows(InvalidActivityPriceException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenCapacityIsNotPositive() {
        Activity activity = validActivity().toBuilder().maxCapacity(0).build();

        assertThrows(InvalidActivityCapacityException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenDurationIsNotPositive() {
        Activity activity = validActivity().toBuilder().durationMinutes(0).build();

        assertThrows(InvalidActivityRequestException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenScheduleIsInverted() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Activity activity = validActivity().toBuilder()
                .startsAt(startsAt)
                .endsAt(startsAt.minusSeconds(60))
                .build();

        assertThrows(InvalidActivityScheduleException.class, activity::validate);
    }

    @Test
    void shouldThrowWhenDatesAreNull() {
        Activity activity = validActivity().toBuilder().startsAt(null).build();

        assertThrows(InvalidActivityScheduleException.class, activity::validate);
    }

    @Test
    void shouldPublishDraftActivity() {
        Activity activity = validActivity();

        activity.changeStatus(ActivityStatus.PUBLISHED);

        assertEquals(ActivityStatus.PUBLISHED, activity.getStatus());
    }

    @Test
    void shouldPausePublishedActivity() {
        Activity activity = validActivity().toBuilder().status(ActivityStatus.PUBLISHED).build();

        activity.changeStatus(ActivityStatus.PAUSED);

        assertEquals(ActivityStatus.PAUSED, activity.getStatus());
    }

    @Test
    void shouldCancelPublishedActivity() {
        Activity activity = validActivity().toBuilder().status(ActivityStatus.PUBLISHED).build();

        activity.changeStatus(ActivityStatus.CANCELLED);

        assertEquals(ActivityStatus.CANCELLED, activity.getStatus());
    }

    @Test
    void shouldThrowWhenPausingCancelledActivity() {
        Activity activity = validActivity().toBuilder().status(ActivityStatus.CANCELLED).build();

        assertThrows(InvalidActivityStatusTransitionException.class, () -> activity.changeStatus(ActivityStatus.PAUSED));
    }

    @Test
    void shouldThrowDomainExceptionWhenChangingStatusToNull() {
        Activity activity = validActivity();

        assertThrows(InvalidActivityStatusTransitionException.class, () -> activity.changeStatus(null));
    }

    @Test
    void shouldUpdateDetails() {
        Activity activity = validActivity();

        activity.updateDetails("Sunset Kayak Tour", "Nautical", "New description", new BigDecimal("99999.00"));

        assertEquals("Sunset Kayak Tour", activity.getName());
        assertEquals("Nautical", activity.getCategory());
        assertEquals("New description", activity.getDescription());
        assertEquals(new BigDecimal("99999.00"), activity.getPrice());
    }

    @Test
    void shouldUpdateSchedule() {
        Activity activity = validActivity();
        Instant newStart = Instant.now().plusSeconds(7200);
        Instant newEnd = newStart.plusSeconds(3600);

        activity.updateSchedule(newStart, newEnd);

        assertEquals(newStart, activity.getStartsAt());
        assertEquals(newEnd, activity.getEndsAt());
    }

    @Test
    void shouldChangeMaxCapacity() {
        Activity activity = validActivity();

        activity.changeMaxCapacity(25);

        assertEquals(25, activity.getMaxCapacity());
    }

    @Test
    void shouldThrowWhenChangingCapacityToZero() {
        Activity activity = validActivity();

        assertThrows(InvalidActivityCapacityException.class, () -> activity.changeMaxCapacity(0));
    }
}
