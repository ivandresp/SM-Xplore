package com.smxplore.activitiesevents.domain.model.reservation;

import com.smxplore.activitiesevents.domain.exceptions.reservation.CancellationWindowClosedException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemPriceException;
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

    /** A tourist may cancel up to this long before the booked start. */
    public static final Duration CANCELLATION_WINDOW = Duration.ofHours(24);

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID touristId;
    private final ReservationItemType itemType;
    private final UUID itemId;

    private BigDecimal unitPrice;
    private final int numberOfPeople;

    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;
    private final PaymentMethod paymentMethod;

    @Builder.Default
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Builder.Default
    private final Instant bookedAt = Instant.now();
    private final Instant bookedFor;

    @Setter
    private String notes;
    private String code;
    private UUID providerId;
    private UUID guideId;

    public void validate() {
        validateTourist();
        validateItem();
        validateNumberOfPeople();
        validateBookingDate();
    }

    public void changeUnitPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidReservationItemPriceException("Unit price must not be null or negative.");
        }
        this.unitPrice = price;
    }

    public void changePrice(BigDecimal newPrice) {
        validateTotal(newPrice);
        total = newPrice;
    }

    public void assignGuide(UUID guideId) {
        this.guideId = guideId;
    }

    public void assignProvider(UUID providerId) {
        this.providerId = providerId;
    }

    public void cancel() {
        changeStatus(ReservationStatus.CANCELLED);
    }

    /** The tourist path: the 24-hour window applies. Staff cancels through {@link #cancel()}. */
    public void cancelAsTourist(Instant now) {
        if (bookedFor == null || now.plus(CANCELLATION_WINDOW).isAfter(bookedFor)) {
            throw new CancellationWindowClosedException(
                    "A reservation can only be cancelled at least %d hours before the start."
                            .formatted(CANCELLATION_WINDOW.toHours()));
        }
        cancel();
    }

    public void registerNoShow() {
        changeStatus(ReservationStatus.NO_SHOW);
    }

    public void registerAttendance() {
        changeStatus(ReservationStatus.COMPLETED);
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
