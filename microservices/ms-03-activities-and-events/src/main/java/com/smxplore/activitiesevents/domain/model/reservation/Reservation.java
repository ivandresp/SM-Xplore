package com.smxplore.activitiesevents.domain.model.reservation;

import com.smxplore.activitiesevents.domain.exceptions.reservation.CancellationWindowClosedException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationRequestException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTotalException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
@Getter
public class Reservation {

    /** A tourist may cancel up to this long before the item starts. */
    public static final Duration CANCELLATION_WINDOW = Duration.ofHours(24);

    /** Upper bound for the client retry token; it also caps the database column. */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID touristId;

    /** Client-supplied retry token, unique per tourist. Null when the client does not send one. */
    private final String idempotencyKey;
    private final ReservationItemType itemType;
    private final UUID itemId;

    /** Start of the booked event/activity, copied from the catalog when the reservation is created. */
    private final Instant itemStartsAt;

    private BigDecimal unitPrice;
    private final int numberOfPeople;

    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;
    private final PaymentMethod paymentMethod;

    @Builder.Default
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Builder.Default
    private final Instant bookedAt = Instant.now();

    /** Always the item start: the server overrides whatever the client sends. */
    private Instant bookedFor;

    @Setter
    private String notes;
    private String code;
    private UUID providerId;
    private UUID guideId;

    public void validate() {
        validateTourist();
        validateIdempotencyKey();
        validateItem();
        validateNumberOfPeople();
        validateBookingDate();
        validateTotal();
    }

    /** Called after the item has been resolved: the schedule and the priced total must be consistent. */
    public void validatePricing() {
        if (itemStartsAt == null) {
            throw new InvalidReservationItemException("The item start is mandatory.");
        }
        validateBookingDate();
        validateTotal();
    }

    public void cancel() {
        changeStatus(ReservationStatus.CANCELLED);
    }

    /** The tourist path: the 24-hour window applies. Staff cancels through {@link #cancel()}. */
    public void cancelAsTourist(Instant now) {
        if (itemStartsAt == null || now.plus(CANCELLATION_WINDOW).isAfter(itemStartsAt)) {
            throw new CancellationWindowClosedException(
                    "A reservation can only be cancelled at least %d hours before the start."
                            .formatted(CANCELLATION_WINDOW.toHours()));
        }
        cancel();
    }

    public void registerNoShow(Instant now) {
        requireStarted(now, "A no-show can only be registered after the item has started.");
        changeStatus(ReservationStatus.NO_SHOW);
    }

    public void registerAttendance(Instant now) {
        requireStarted(now, "Attendance can only be registered after the item has started.");
        changeStatus(ReservationStatus.COMPLETED);
    }

    private void requireStarted(Instant now, String message) {
        if (itemStartsAt == null || now.isBefore(itemStartsAt)) {
            throw new InvalidReservationStatusTransitionException(message);
        }
    }

    private void changeStatus(ReservationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidReservationStatusTransitionException(
                    "Cannot change reservation from %s to %s".formatted(status, target));
        }
        status = target;
    }

    private void validateTourist() {
        if (touristId == null) {
            throw new InvalidReservationTouristException("Tourist is mandatory.");
        }
    }

    private void validateIdempotencyKey() {
        if (idempotencyKey == null) {
            return;
        }
        var key = idempotencyKey.trim();
        if (key.isEmpty() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidReservationRequestException(
                    "Idempotency key must have between 1 and %d characters."
                            .formatted(MAX_IDEMPOTENCY_KEY_LENGTH));
        }
    }

    private void validateItem() {
        if (itemType == null) {
            throw new InvalidReservationItemException("Item type is mandatory.");
        }
        if (itemId == null) {
            throw new InvalidReservationItemException("Item id is mandatory.");
        }
    }

    private void validateNumberOfPeople() {
        if (numberOfPeople <= 0) {
            throw new InvalidNumberOfPeopleException("Number of people must be greater than zero.");
        }
    }

    private void validateTotal() {
        validateTotal(total);
        if (unitPrice != null) {
            var expected = unitPrice.multiply(BigDecimal.valueOf(numberOfPeople));
            if (total.compareTo(expected) != 0) {
                throw new InvalidReservationTotalException(
                        "The total must be the unit price times the number of people.");
            }
        }
    }

    private void validateTotal(BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidReservationTotalException("Total must not be null or negative.");
        }
    }

    private void validateBookingDate() {
        if (bookedFor == null || bookedAt == null || !bookedFor.isAfter(bookedAt)) {
            throw new InvalidBookingDateException("Booking date must be in the future.");
        }
    }
}
