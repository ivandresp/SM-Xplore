package com.smxplore.activitiesevents.domain.model.event;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventNameException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventPriceException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidExpectedAttendanceException;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Event {

    private static final int NAME_MAX_LENGTH = 150;
    private static final int CATEGORY_MAX_LENGTH = 50;

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID siteId;
    private String name;
    private String category;

    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    private int maxCapacity;
    private Instant startsAt;
    private Instant endsAt;
    private String description;

    @Builder.Default
    private int expectedAttendance = 0;

    public void validate() {
        validateSite();
        validateName();
        validateCategory();
        validatePrice();
        validateCapacity();
        validateSchedule();
        validateExpectedAttendance();
    }

    public void changeStatus(EventStatus target) {
        if (!status.canTransitionTo(target))
            throw new InvalidEventStatusTransitionException(
                    "Cannot change event from %s to %s".formatted(status, target));
        status = target;
    }

    public void estimateAttendance(int expectedAttendance) {
        if (expectedAttendance < 0)
            throw new InvalidExpectedAttendanceException("Expected attendance must not be negative.");
        this.expectedAttendance = expectedAttendance;
    }

    private void validateSite() {
        if (siteId == null)
            throw new InvalidEventRequestException("Site is mandatory.");
    }

    private void validateName() {
        if (name == null || name.isBlank())
            throw new InvalidEventNameException("Name must not be blank.");
        if (name.strip().length() > NAME_MAX_LENGTH)
            throw new InvalidEventNameException("Name must not exceed %d characters.".formatted(NAME_MAX_LENGTH));
    }

    private void validateCategory() {
        if (category == null || category.isBlank())
            throw new InvalidEventRequestException("Category must not be blank.");
        if (category.strip().length() > CATEGORY_MAX_LENGTH)
            throw new InvalidEventRequestException("Category must not exceed %d characters.".formatted(CATEGORY_MAX_LENGTH));
    }

    private void validatePrice() {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0)
            throw new InvalidEventPriceException("Price must not be null or negative.");
    }

    private void validateCapacity() {
        if (maxCapacity <= 0)
            throw new InvalidEventCapacityException("Max capacity must be greater than zero.");
    }

    private void validateSchedule() {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
            throw new InvalidEventScheduleException("Event schedule must have an end date after the start date.");
    }

    private void validateExpectedAttendance() {
        if (expectedAttendance < 0)
            throw new InvalidExpectedAttendanceException("Expected attendance must not be negative.");
    }
}
