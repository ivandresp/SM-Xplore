package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationRequestException;
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
 * locked, capacity is checked against the people count, the price comes from
 * the catalog and the reservation is stored already CONFIRMED.
 * <p>
 * The caller must wrap this use case in a transaction so the item lock is held
 * until the reservation is inserted.
 */
@RequiredArgsConstructor
public class CreateReservationUseCase implements CreateReservationUseCasePort {

    private final ReservationRepositoryPort reservationRepo;
    private final ActivityRepositoryPort activityRepo;
    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<Reservation> handle(Reservation reservation) {
        return Mono.justOrEmpty(reservation)
                .switchIfEmpty(Mono.error(new InvalidReservationRequestException("Reservation cannot be null")))
                .doOnNext(Reservation::validate)
                .flatMap(this::resolveItem)
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
                            .thenReturn(applyPrice(reservation, activity.getPrice(), activity.getGuideId(), null));
                });
    }

    private Mono<Reservation> resolveEvent(Reservation reservation) {
        return eventRepo.findByIdForUpdate(reservation.getItemId())
                .switchIfEmpty(Mono.error(new EventNotFoundException(reservation.getItemId())))
                .flatMap(event -> {
                    validateEvent(event);
                    return validateCapacity(reservation, event.getMaxCapacity())
                            .thenReturn(applyPrice(reservation, event.getPrice(), null, event.getManagerId()));
                });
    }

    private void validateActivity(Activity activity) {
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            throw new InvalidReservationItemException(
                    "Activity %s is not published.".formatted(activity.getId()));
        }
        if (activity.getEndsAt() == null || !activity.getEndsAt().isAfter(Instant.now())) {
            throw new InvalidBookingDateException(
                    "Activity %s has already ended.".formatted(activity.getId()));
        }
    }

    private void validateEvent(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidReservationItemException(
                    "Event %s is not published.".formatted(event.getId()));
        }
        if (event.getEndsAt() == null || !event.getEndsAt().isAfter(Instant.now())) {
            throw new InvalidBookingDateException(
                    "Event %s has already ended.".formatted(event.getId()));
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

    private Reservation applyPrice(Reservation reservation, BigDecimal unitPrice, UUID guideId, UUID providerId) {
        reservation.changeUnitPrice(unitPrice);
        reservation.changePrice(unitPrice.multiply(BigDecimal.valueOf(reservation.getNumberOfPeople())));
        reservation.assignGuide(guideId);
        reservation.assignProvider(providerId);
        return reservation;
    }
}
