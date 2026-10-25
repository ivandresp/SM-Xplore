package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.DifferentProvidersInReservationException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.MixedReservationItemTypesException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyReservationItemsException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Business rules for the items of a reservation:
 * <ul>
 *     <li>The reservation must exist and be in PENDING state to accept items.</li>
 *     <li>All the items of a reservation must share the same type; there is a
 *     maximum of {@value #MAX_ITEMS_PER_RESERVATION} items per reservation.</li>
 *     <li>ACTIVITY items must belong to published activities of the same guide;
 *     EVENT items must belong to a published event and are limited to one per
 *     reservation.</li>
 *     <li>The price always comes from the catalog, never from the client.</li>
 *     <li>Capacity is checked against the item's max capacity using the people
 *     count of the reservation and the active reservations already booked.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class AddReservationItemsUseCase implements AddReservationItemsUseCasePort {

    static final int MAX_ITEMS_PER_RESERVATION = 100;

    private final ReservationRepositoryPort reservationRepo;
    private final ReservationItemRepositoryPort reservationItemRepo;
    private final ActivityRepositoryPort activityRepo;
    private final EventRepositoryPort eventRepo;

    @Override
    public Flux<ReservationItem> handle(UUID reservationId, Collection<ReservationItem> items) {
        return Mono.fromCallable(() -> prepareItems(reservationId, items))
                .flatMapMany(prepared -> reservationRepo.findByIdForUpdate(reservationId)
                        .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                        .flatMapMany(reservation -> {
                            reservation.validateItemsCanBeAdded();
                            return reservationItemRepo.findByReservationId(reservationId)
                                    .collectList()
                                    .flatMapMany(existing -> resolveItems(reservation, existing, prepared)
                                            .flatMapMany(resolved -> Flux.defer(() -> reservationItemRepo.saveAll(resolved))
                                                    .collectList()
                                                    .flatMap(saved -> assignOwner(reservation, resolved)
                                                            .then(Mono.defer(() -> recalculateTotal(reservation,
                                                                    existing, saved)))
                                                            .thenReturn(saved))
                                                    .flatMapMany(Flux::fromIterable)));
                        }));
    }

    private List<ReservationItem> prepareItems(UUID reservationId, Collection<ReservationItem> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidReservationItemException("At least one reservation item is required.");
        }
        var seen = new HashSet<UUID>();
        var prepared = items.stream()
                .map(item -> prepareItem(reservationId, item))
                .peek(item -> {
                    if (!seen.add(item.getItemId())) {
                        throw new InvalidReservationItemException(
                                "Duplicate item %s in the same request.".formatted(item.getItemId()));
                    }
                })
                .toList();
        if (prepared.stream().map(ReservationItem::getType).distinct().count() > 1) {
            throw new MixedReservationItemTypesException("All the items of a reservation must have the same type.");
        }
        return prepared;
    }

    private ReservationItem prepareItem(UUID reservationId, ReservationItem item) {
        if (item == null) {
            throw new InvalidReservationItemException("Reservation item cannot be null.");
        }
        if (item.getItemId() == null) {
            throw new InvalidReservationItemException("Item id is mandatory.");
        }
        if (item.getType() == null) {
            throw new InvalidReservationItemException("Item type is mandatory.");
        }
        return item.toBuilder().reservationId(reservationId).build();
    }

    private Mono<List<ReservationItem>> resolveItems(Reservation reservation, List<ReservationItem> existing,
                                                     List<ReservationItem> incoming) {
        var incomingType = incoming.getFirst().getType();
        if (!existing.isEmpty() && existing.getFirst().getType() != incomingType) {
            return Mono.error(new MixedReservationItemTypesException(
                    "The reservation already has %s items, it cannot accept %s items."
                            .formatted(existing.getFirst().getType(), incomingType)));
        }
        if (existing.size() + incoming.size() > MAX_ITEMS_PER_RESERVATION) {
            return Mono.error(new TooManyReservationItemsException(
                    "A reservation cannot have more than %d items.".formatted(MAX_ITEMS_PER_RESERVATION)));
        }
        if (incomingType == ReservationItemType.EVENT && existing.size() + incoming.size() > 1) {
            return Mono.error(new TooManyReservationItemsException("An EVENT reservation can only have one item."));
        }
        var existingIds = existing.stream().map(ReservationItem::getItemId).toList();
        if (incoming.stream().anyMatch(item -> existingIds.contains(item.getItemId()))) {
            return Mono.error(new TooManyReservationItemsException(
                    "An item cannot be added twice to the same reservation."));
        }
        return switch (incomingType) {
            case ACTIVITY -> resolveActivities(reservation, existing, incoming);
            case EVENT -> resolveEvents(reservation, incoming);
        };
    }

    private Mono<List<ReservationItem>> resolveActivities(Reservation reservation, List<ReservationItem> existing,
                                                          List<ReservationItem> incoming) {
        var activityIds = Stream.concat(existing.stream(), incoming.stream())
                .map(ReservationItem::getItemId)
                .distinct()
                .toList();
        return Flux.fromIterable(activityIds)
                .concatMap(activityId -> activityRepo.findByIdForUpdate(activityId)
                        .switchIfEmpty(Mono.error(new ActivityNotFoundException(activityId))))
                .collectMap(Activity::getId)
                .flatMap(activities -> {
                    var guides = activities.values().stream()
                            .map(Activity::getGuideId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();
                    if (guides > 1) {
                        return Mono.error(new DifferentProvidersInReservationException(
                                "All the activities of a reservation must be offered by the same guide."));
                    }
                    return Flux.fromIterable(activities.values())
                            .concatMap(activity -> validateActivity(reservation, activity))
                            .then(Mono.fromCallable(() -> {
                                var resolved = incoming.stream()
                                        .map(item -> item.toBuilder()
                                                .price(activities.get(item.getItemId()).getPrice()).build())
                                        .toList();
                                return validate(resolved);
                            }));
                });
    }

    private Mono<List<ReservationItem>> resolveEvents(Reservation reservation, List<ReservationItem> incoming) {
        var eventIds = incoming.stream()
                .map(ReservationItem::getItemId)
                .distinct()
                .toList();
        return Flux.fromIterable(eventIds)
                .concatMap(eventId -> eventRepo.findByIdForUpdate(eventId)
                        .switchIfEmpty(Mono.error(new EventNotFoundException(eventId))))
                .collectMap(Event::getId)
                .flatMap(events -> Flux.fromIterable(events.values())
                        .concatMap(event -> validateEvent(reservation, event))
                        .then(Mono.fromCallable(() -> {
                            var resolved = incoming.stream()
                                    .map(item -> item.toBuilder()
                                            .price(events.get(item.getItemId()).getPrice()).build())
                                    .toList();
                            return validate(resolved);
                        })));
    }

    private Mono<Void> validateActivity(Reservation reservation, Activity activity) {
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            return Mono.error(new InvalidReservationItemException(
                    "Activity %s is not published.".formatted(activity.getId())));
        }
        if (activity.getEndsAt() == null || !activity.getEndsAt().isAfter(Instant.now())) {
            return Mono.error(new InvalidBookingDateException(
                    "Activity %s has already ended.".formatted(activity.getId())));
        }
        return validateCapacity(reservation, activity.getId(), ReservationItemType.ACTIVITY,
                activity.getMaxCapacity());
    }

    private Mono<Void> validateEvent(Reservation reservation, Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return Mono.error(new InvalidReservationItemException(
                    "Event %s is not published.".formatted(event.getId())));
        }
        if (event.getEndsAt() == null || !event.getEndsAt().isAfter(Instant.now())) {
            return Mono.error(new InvalidBookingDateException(
                    "Event %s has already ended.".formatted(event.getId())));
        }
        return validateCapacity(reservation, event.getId(), ReservationItemType.EVENT, event.getMaxCapacity());
    }

    private Mono<Void> validateCapacity(Reservation reservation, UUID itemId, ReservationItemType type,
                                        int maxCapacity) {
        if (reservation.getNumberOfPeople() > maxCapacity) {
            return Mono.error(new InvalidNumberOfPeopleException(
                    "The reservation exceeds the max capacity (%d).".formatted(maxCapacity)));
        }
        return reservationItemRepo.sumActivePeopleForItem(reservation.getId(), itemId, type)
                .flatMap(reserved -> reserved + reservation.getNumberOfPeople() > maxCapacity
                        ? Mono.error(new InvalidNumberOfPeopleException(
                                "Not enough capacity for this item: %d of %d seats are already reserved."
                                        .formatted(reserved, maxCapacity)))
                        : Mono.empty());
    }

    /** Fixes the guide (ACTIVITY items) of the reservation. */
    private Mono<Void> assignOwner(Reservation reservation, List<ReservationItem> items) {
        return switch (items.getFirst().getType()) {
            case ACTIVITY -> Flux.fromIterable(items)
                    .map(ReservationItem::getItemId)
                    .distinct()
                    .concatMap(activityRepo::findById)
                    .mapNotNull(Activity::getGuideId)
                    .distinct()
                    .singleOrEmpty()
                    .doOnNext(reservation::assignGuide)
                    .then();
            case EVENT -> Mono.empty();
        };
    }

    private List<ReservationItem> validate(List<ReservationItem> items) {
        items.forEach(ReservationItem::validate);
        return items;
    }

    /**
     * The total is the sum of the item prices multiplied by the number of people.
     */
    private Mono<Void> recalculateTotal(Reservation reservation, List<ReservationItem> existing,
                                        List<ReservationItem> saved) {
        var unitTotal = Stream.concat(existing.stream(), saved.stream())
                .map(ReservationItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        reservation.changePrice(unitTotal.multiply(BigDecimal.valueOf(reservation.getNumberOfPeople())));
        return reservationRepo.save(reservation).then();
    }
}
