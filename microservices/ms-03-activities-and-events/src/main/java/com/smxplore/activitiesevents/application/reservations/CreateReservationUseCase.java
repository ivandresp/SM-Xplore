package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationRequestException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyActiveReservationsException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates a symbolic reservation in a single step: the item is validated and
 * locked, capacity is checked against the people count, the price and the start
 * come from the catalog and the reservation is stored already CONFIRMED.
 * <p>
 * The caller must wrap this use case in a transaction so the item lock is held
 * until the reservation is inserted. The tourist advisory lock is taken first:
 * a retry with the same idempotency key waits for the first attempt and then
 * reads its reservation instead of racing the capacity check.
 */
@RequiredArgsConstructor
public class CreateReservationUseCase implements CreateReservationUseCasePort {

    /** Anti-abuse rule for a flow without payment: one tourist cannot hold the whole catalog. */
    static final int MAX_ACTIVE_RESERVATIONS_PER_TOURIST = 5;

    private final ReservationRepositoryPort reservationRepo;
    private final ActivityRepositoryPort activityRepo;
    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<Reservation> handle(Reservation reservation) {
        return Mono.justOrEmpty(reservation)
                .switchIfEmpty(Mono.error(new InvalidReservationRequestException("Reservation cannot be null")))
                .doOnNext(Reservation::validate)
                .flatMap(this::createOrReplay);
    }

    private Mono<Reservation> createOrReplay(Reservation reservation) {
        return reservationRepo.lockTourist(reservation.getTouristId())
                .then(Mono.defer(() -> findReplay(reservation)))
                .switchIfEmpty(Mono.defer(() -> create(reservation)));
    }

    private Mono<Reservation> findReplay(Reservation reservation) {
        if (reservation.getIdempotencyKey() == null) {
            return Mono.empty();
        }
        return reservationRepo.findByTouristIdAndIdempotencyKey(
                reservation.getTouristId(), reservation.getIdempotencyKey());
    }

    private Mono<Reservation> create(Reservation reservation) {
        return Mono.just(reservation)
                .flatMap(this::resolveItem)
                .doOnNext(Reservation::validatePricing)
                .flatMap(reservationRepo::save);
    }

    private Mono<Reservation> resolveItem(Reservation reservation) {
        return switch (reservation.getItemType()) {
            case ACTIVITY -> resolveActivity(reservation);
            case EVENT -> resolveEvent(reservation);
        };
    }

    private Mono<Reservation> resolveActivity(Reservation reservation) {
        return activityRepo.findByIdForUpdate(reservation.getItemId())
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(reservation.getItemId())))
                .flatMap(activity -> {
                    validateActivity(activity);
                    return validateCapacity(reservation, activity.getMaxCapacity())
                            .then(Mono.defer(() -> validateActiveReservations(reservation)))
                            .thenReturn(priced(reservation, activity.getPrice(), activity.getStartsAt(),
                                    activity.getGuideId(), null));
                });
    }

    private Mono<Reservation> resolveEvent(Reservation reservation) {
        return eventRepo.findByIdForUpdate(reservation.getItemId())
                .switchIfEmpty(Mono.error(new EventNotFoundException(reservation.getItemId())))
                .flatMap(event -> {
                    validateEvent(event);
                    return validateCapacity(reservation, event.getMaxCapacity())
                            .then(Mono.defer(() -> validateActiveReservations(reservation)))
                            .thenReturn(priced(reservation, event.getPrice(), event.getStartsAt(),
                                    null, event.getManagerId()));
                });
    }

    private void validateActivity(Activity activity) {
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            throw new InvalidReservationItemException(
                    "Activity %s is not published.".formatted(activity.getId()));
        }
        validateSchedule(activity.getStartsAt(), activity.getEndsAt(), "Activity " + activity.getId());
    }

    private void validateEvent(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidReservationItemException(
                    "Event %s is not published.".formatted(event.getId()));
        }
        validateSchedule(event.getStartsAt(), event.getEndsAt(), "Event " + event.getId());
    }

    private void validateSchedule(Instant startsAt, Instant endsAt, String label) {
        if (startsAt == null || !startsAt.isAfter(Instant.now())) {
            throw new InvalidBookingDateException("%s has already started.".formatted(label));
        }
        if (endsAt == null || !endsAt.isAfter(Instant.now())) {
            throw new InvalidBookingDateException("%s has already ended.".formatted(label));
        }
    }

    private Mono<Void> validateCapacity(Reservation reservation, int maxCapacity) {
        if (reservation.getNumberOfPeople() > maxCapacity) {
            return Mono.error(new InvalidNumberOfPeopleException(
                    "The reservation exceeds the max capacity (%d).".formatted(maxCapacity)));
        }
        return reservationRepo.sumActivePeopleForItem(reservation.getItemId(), reservation.getItemType())
                .flatMap(reserved -> reserved + reservation.getNumberOfPeople() > maxCapacity
                        ? Mono.error(new InvalidNumberOfPeopleException(
                                "Not enough capacity for this item: %d of %d seats are already reserved."
                                        .formatted(reserved, maxCapacity)))
                        : Mono.empty());
    }

    private Mono<Void> validateActiveReservations(Reservation reservation) {
        return reservationRepo.countActiveByTourist(reservation.getTouristId())
                .flatMap(active -> active >= MAX_ACTIVE_RESERVATIONS_PER_TOURIST
                        ? Mono.error(new TooManyActiveReservationsException(
                                "A tourist cannot have more than %d active reservations."
                                        .formatted(MAX_ACTIVE_RESERVATIONS_PER_TOURIST)))
                        : Mono.empty());
    }

    private Reservation priced(Reservation reservation, BigDecimal unitPrice, Instant startsAt,
                               UUID guideId, UUID providerId) {
        var total = unitPrice.multiply(BigDecimal.valueOf(reservation.getNumberOfPeople()));
        return reservation.toBuilder()
                .itemStartsAt(startsAt)
                .unitPrice(unitPrice)
                .total(total)
                .bookedFor(startsAt)
                .guideId(guideId)
                .providerId(providerId)
                .build();
    }
}
