package com.smxplore.activitiesevents.domain.model.reservation;

import com.smxplore.activitiesevents.domain.exceptions.reservation.DifferentProvidersInReservationException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTotalException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationItemsNotAllowedException;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
@Getter
public class Reservation {
    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID touristId;
    private final int numberOfPeople;

    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;
    private final PaymentMethod paymentMethod;

    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

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
        validateNumberOfPeople();
        validateTotal(total);
        validateBookingDate();
    }

    /**
     * Una reserva en estado final (cancelada, rechazada, completada o no-show)
     * ya no admite cambios en sus items.
     */
    public void validateItemsCanBeAdded() {
        if (status.isFinal()) {
            throw new ReservationItemsNotAllowedException(
                    "Items cannot be added to a reservation in status %s".formatted(status));
        }
    }

    public void changePrice(BigDecimal newPrice) {
        validateTotal(newPrice);
        total = newPrice;
    }

    /**
     * The provider is fixed by the first SERVICE item of the reservation; every
     * later item must belong to the same provider.
     */
    public void assignProvider(UUID providerId) {
        if (providerId == null) {
            return;
        }
        if (this.providerId != null && !this.providerId.equals(providerId)) {
            throw new DifferentProvidersInReservationException(
                    "All the services of a reservation must be offered by the same provider.");
        }
        this.providerId = providerId;
    }

    /**
     * The guide is fixed by the first ACTIVITY item of the reservation; every
     * later item must belong to the same guide.
     */
    public void assignGuide(UUID guideId) {
        if (guideId == null) {
            return;
        }
        if (this.guideId != null && !this.guideId.equals(guideId)) {
            throw new DifferentProvidersInReservationException(
                    "All the activities of a reservation must be offered by the same guide.");
        }
        this.guideId = guideId;
    }

    public void confirm() {
        changeStatus(ReservationStatus.CONFIRMED);
    }

    public void reject() {
        changeStatus(ReservationStatus.REJECTED);
    }

    public void requestCancellation() {
        changeStatus(ReservationStatus.CANCELLATION_REQUESTED);
    }

    public void cancel() {
        changeStatus(ReservationStatus.CANCELLED);
    }

    public void registerNoShow() {
        changeStatus(ReservationStatus.NO_SHOW);
    }

    public void registerAttendance() {
        changeStatus(ReservationStatus.COMPLETED);
    }

    private void changeStatus(ReservationStatus target) {
        if (!status.canTransitionTo(target))
            throw new InvalidReservationStatusTransitionException(
                    "Cannot change reservation from %s to %s".formatted(status, target));
        status = target;
    }

    private void validateTourist() {
        if (touristId == null)
            throw new InvalidReservationTouristException("Tourist is mandatory.");
    }

    private void validateNumberOfPeople() {
        if (numberOfPeople <= 0)
            throw new InvalidNumberOfPeopleException("Number of people must be greater than zero.");
    }

    private void validateTotal(BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0)
            throw new InvalidReservationTotalException("Total must not be null or negative.");
    }

    private void validateBookingDate() {
        if (bookedFor == null || bookedAt == null || !bookedFor.isAfter(bookedAt))
            throw new InvalidBookingDateException("Booking date must be in the future.");
    }
}