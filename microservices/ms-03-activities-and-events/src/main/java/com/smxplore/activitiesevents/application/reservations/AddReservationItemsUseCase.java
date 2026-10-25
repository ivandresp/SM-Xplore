package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.DifferentProvidersInReservationException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.MixedReservationItemTypesException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyReservationItemsException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.event.Event;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Business rules for the items of a reservation:
 * <ul>
 *     <li>The reservation must exist and not be in a final state.</li>
 *     <li>All the items of a reservation must share the same type; there is a
 *     maximum of {@value #MAX_ITEMS_PER_RESERVATION} items per reservation.</li>
 *     <li>ACTIVITY items must belong to activities of the same guide; their price
 *     comes from the catalog (the client-sent price is ignored).</li>
 *     <li>EVENT items are limited to one per reservation and their price also
 *     comes from the event catalog.</li>
 *     <li>The reservation remembers its guide (ACTIVITY items) so each guide can
 *     list the reservations they received.</li>
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
                                    .flatMapMany(existing -> resolveItems(existing, prepared)
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
        var prepared = items.stream()
                .map(item -> prepareItem(reservationId, item))
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

    private Mono<List<ReservationItem>> resolveItems(List<ReservationItem> existing, List<ReservationItem> incoming) {
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
        return switch (incomingType) {
            case ACTIVITY -> resolveActivities(existing, incoming);
            case EVENT -> resolveEvents(incoming);
        };
    }

    private Mono<List<ReservationItem>> resolveActivities(List<ReservationItem> existing, List<ReservationItem> incoming) {
        var activityIds = Stream.concat(existing.stream(), incoming.stream())
                .map(ReservationItem::getItemId)
                .distinct()
                .toList();
        return Flux.fromIterable(activityIds)
                .concatMap(activityId -> activityRepo.findById(activityId)
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
                    var resolved = incoming.stream()
                            .map(item -> item.toBuilder().price(activities.get(item.getItemId()).getPrice()).build())
                            .toList();
                    return Mono.just(validate(resolved));
                });
    }

    private Mono<List<ReservationItem>> resolveEvents(List<ReservationItem> incoming) {
        var eventIds = incoming.stream()
                .map(ReservationItem::getItemId)
                .distinct()
                .toList();
        return Flux.fromIterable(eventIds)
                .concatMap(eventId -> eventRepo.findById(eventId)
                        .switchIfEmpty(Mono.error(new EventNotFoundException(eventId))))
                .collectMap(Event::getId)
                .flatMap(events -> {
                    var resolved = incoming.stream()
                            .map(item -> item.toBuilder().price(events.get(item.getItemId()).getPrice()).build())
                            .toList();
                    return Mono.just(validate(resolved));
                });
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
     * The total of the reservation is the sum of the prices of all its items.
     */
    private Mono<Void> recalculateTotal(Reservation reservation, List<ReservationItem> existing,
                                        List<ReservationItem> saved) {
        var total = Stream.concat(existing.stream(), saved.stream())
                .map(ReservationItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        reservation.changePrice(total);
        return reservationRepo.save(reservation).then();
    }
}
