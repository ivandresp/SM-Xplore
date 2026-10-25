package com.smxplore.activitiesevents.domain.model.activity;

import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityNameException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityPriceException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityScheduleException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityStatusTransitionException;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Activity {

    private static final int NAME_MAX_LENGTH = 150;
    private static final int CATEGORY_MAX_LENGTH = 50;

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID siteId;
    private final UUID guideId;
    private String name;
    private String category;

    @Builder.Default
    private ActivityStatus status = ActivityStatus.DRAFT;

    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    private int maxCapacity;
    private int durationMinutes;
    private Instant startsAt;
    private Instant endsAt;
    private String description;

    public void validate() {
        validateSite();
        validateName(name);
        validateCategory(category);
        validatePrice(price);
        validateCapacity(maxCapacity);
        validateDuration(durationMinutes);
        validateSchedule(startsAt, endsAt);
    }

    public void updateDetails(String name, String category, String description, BigDecimal price) {
        requireMutable();
        validateName(name);
        validateCategory(category);
        validatePrice(price);
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
    }

    public void updateSchedule(Instant startsAt, Instant endsAt) {
        requireMutable();
        validateSchedule(startsAt, endsAt);
        var minutes = (int) Duration.between(startsAt, endsAt).toMinutes();
        if (minutes <= 0)
            throw new InvalidActivityScheduleException("Activity schedule must last at least one minute.");
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.durationMinutes = minutes;
    }

    public void changeMaxCapacity(int maxCapacity) {
        requireMutable();
        validateCapacity(maxCapacity);
        this.maxCapacity = maxCapacity;
    }

    public void changeStatus(ActivityStatus target) {
        if (!status.canTransitionTo(target))
            throw new InvalidActivityStatusTransitionException(
                    "Cannot change activity from %s to %s".formatted(status, target));
        status = target;
    }

    private void requireMutable() {
        if (status.isFinal())
            throw new InvalidActivityStatusTransitionException("Cannot modify a cancelled activity.");
    }

    private void validateSite() {
        if (siteId == null)
            throw new InvalidActivityRequestException("Site is mandatory.");
    }

    private void validateName(String name) {
        if (name == null || name.isBlank())
            throw new InvalidActivityNameException("Name must not be blank.");
        if (name.strip().length() > NAME_MAX_LENGTH)
            throw new InvalidActivityNameException("Name must not exceed %d characters.".formatted(NAME_MAX_LENGTH));
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank())
            throw new InvalidActivityRequestException("Category must not be blank.");
        if (category.strip().length() > CATEGORY_MAX_LENGTH)
            throw new InvalidActivityRequestException("Category must not exceed %d characters.".formatted(CATEGORY_MAX_LENGTH));
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0)
            throw new InvalidActivityPriceException("Price must not be null or negative.");
    }

    private void validateCapacity(int maxCapacity) {
        if (maxCapacity <= 0)
            throw new InvalidActivityCapacityException("Max capacity must be greater than zero.");
    }

    private void validateDuration(int durationMinutes) {
        if (durationMinutes <= 0)
            throw new InvalidActivityRequestException("Duration must be positive.");
    }

    private void validateSchedule(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
            throw new InvalidActivityScheduleException("Activity schedule must have an end date after the start date.");
    }
}
