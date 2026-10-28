package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.ReservationAccess;
import com.smxplore.sitesandservices.application.support.ReservationAvailabilityPolicy;
import com.smxplore.sitesandservices.application.support.ReservationPolicy;
import com.smxplore.sitesandservices.domain.exception.DomainStateException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ReservationConflictException;
import com.smxplore.sitesandservices.domain.exception.ReservationLimitException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.ports.in.CreateReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationOutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationTransactionPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class ReservationCreateService implements CreateReservationUseCase {

    private static final int MAX_ITEMS = 10;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final ServiceRepositoryPort serviceRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final ReservationTransactionPort transaction;
    private final ReservationOutboxPort outbox;
    private final ReservationAvailabilityPolicy availabilityPolicy;
    private final ReservationAccess access;
    private final DomainEventFactory eventFactory;
    private final ReservationPolicy policy;
    private final Clock clock;

    @Override
    public Mono<Reservation> handle(
            Reservation reservation,
            String idempotencyKey,
            Actor actor,
            String correlationId
    ) {
        return access.requireTourist(actor)
                .flatMap(tourist -> {
                    validateDraft(reservation);
                    String key = normalizeIdempotencyKey(idempotencyKey);
                    String fingerprint = fingerprint(tourist.userId(), reservation);
                    return replay(tourist.userId(), key, fingerprint)
                            .switchIfEmpty(Mono.defer(() -> createNew(
                                    reservation,
                                    tourist,
                                    key,
                                    fingerprint,
                                    correlationId
                            ).onErrorResume(error -> replay(
                                            tourist.userId(), key, fingerprint)
                                    .switchIfEmpty(Mono.error(error)))));
                });
    }

    private Mono<Reservation> replay(UUID touristId, String idempotencyKey, String fingerprint) {
        return reservationRepository.findByIdempotencyKey(touristId, idempotencyKey)
                .flatMap(existing -> {
                    if (!fingerprint.equals(existing.requestFingerprint())) {
                        return Mono.error(new ReservationConflictException(
                                "Idempotency-Key was reused with a different reservation payload"));
                    }
                    return Mono.just(existing);
                });
    }

    private Mono<Reservation> createNew(
            Reservation reservation,
            Actor tourist,
            String idempotencyKey,
            String requestFingerprint,
            String correlationId
    ) {
        Instant now = Instant.now(clock);
        if (!reservation.bookedFor().isAfter(now)) {
            return Mono.error(new InvalidCatalogException(
                    "reservation bookedFor must be in the future"));
        }
        if (reservation.bookedFor().isAfter(
                now.plus(Duration.ofDays(policy.maxAdvanceDays())))) {
            return Mono.error(new InvalidCatalogException(
                    "reservation cannot be booked more than "
                            + policy.maxAdvanceDays() + " days in advance"));
        }
        return requestedServices(reservation)
                .flatMap(requested -> create(
                        reservation,
                        requested,
                        tourist,
                        now,
                        idempotencyKey,
                        requestFingerprint,
                        correlationId
                ));
    }

    private Mono<List<RequestedService>> requestedServices(Reservation reservation) {
        return Flux.range(0, reservation.items().size())
                .concatMap(position -> {
                    ReservationItem draftItem = reservation.items().get(position);
                    return serviceRepository.findById(draftItem.serviceId())
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                                    "Service", draftItem.serviceId())))
                            .map(service -> new RequestedService(position, draftItem, service));
                })
                .collectList();
    }

    private Mono<Reservation> create(
            Reservation reservation,
            List<RequestedService> requested,
            Actor tourist,
            Instant now,
            String idempotencyKey,
            String requestFingerprint,
            String correlationId
    ) {
        return requireSingleProvider(requested)
                .then(Flux.fromIterable(requested)
                        .concatMap(item -> requireAvailable(reservation.bookedFor(), item))
                        .then(Mono.fromSupplier(() -> buildReservation(
                                reservation,
                                requested,
                                tourist,
                                now,
                                idempotencyKey,
                                requestFingerprint
                        ))))
                .flatMap(created -> transaction.execute(Mono.defer(() -> createIdempotently(
                        created,
                        requested,
                        tourist.userId(),
                        idempotencyKey,
                        requestFingerprint,
                        correlationId
                ))));
    }

    private Mono<Void> requireSingleProvider(List<RequestedService> requested) {
        Set<UUID> providerIds = new HashSet<>();
        for (RequestedService item : requested) {
            providerIds.add(item.service().providerId());
        }
        if (providerIds.size() > 1) {
            return Mono.error(new InvalidCatalogException(
                    "reservation items must belong to a single provider"));
        }
        return Mono.empty();
    }

    private Mono<Void> requireAvailable(Instant bookedFor, RequestedService requested) {
        String problem = availabilityPolicy.availabilityProblem(
                requested.service(), bookedFor);
        if (problem == null) {
            return Mono.empty();
        }
        return Mono.error(new ReservationConflictException(
                "Service " + requested.service().name() + " is not available: " + problem));
    }

    private Reservation buildReservation(
            Reservation draft,
            List<RequestedService> requested,
            Actor tourist,
            Instant now,
            String idempotencyKey,
            String requestFingerprint
    ) {
        List<ReservationItem> items = requested.stream()
                .map(this::toItem)
                .toList();
        BigDecimal total = items.stream()
                .map(ReservationItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Reservation created = draft.toBuilder()
                .reservationId(UUID.randomUUID())
                .touristId(tourist.userId())
                .touristName(tourist.displayName())
                .status(ReservationStatus.CONFIRMED)
                .total(total)
                .items(items)
                .createdAt(now)
                .updatedAt(now)
                .confirmedAt(now)
                .cancelledAt(null)
                .cancellationReason(null)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(requestFingerprint)
                .build();
        created.validate();
        return created;
    }

    private ReservationItem toItem(RequestedService requested) {
        Service service = requested.service();
        ReservationItem draftItem = requested.reservation();
        BigDecimal unitPrice = service.price() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : service.price().setScale(2, RoundingMode.HALF_UP);
        return ReservationItem.builder()
                .reservationItemId(UUID.randomUUID())
                .serviceId(service.serviceId())
                .serviceName(service.name())
                .providerId(service.providerId())
                .providerName(service.providerName())
                .serviceType(service.type())
                .unitPrice(unitPrice)
                .people(draftItem.people())
                .total(ReservationItem.calculateTotal(unitPrice, draftItem.people()))
                .position(requested.position())
                .build();
    }

    private Mono<Reservation> createIdempotently(
            Reservation created,
            List<RequestedService> requested,
            UUID touristId,
            String idempotencyKey,
            String requestFingerprint,
            String correlationId
    ) {
        return reservationRepository.lockIdempotencyKey(touristId, idempotencyKey)
                .then(reservationRepository.findByIdempotencyKey(touristId, idempotencyKey))
                .flatMap(existing -> {
                    if (!requestFingerprint.equals(existing.requestFingerprint())) {
                        return Mono.error(new ReservationConflictException(
                                "Idempotency-Key was reused with a different reservation payload"));
                    }
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> persist(
                        created,
                        requested,
                        correlationId
                )));
    }

    private Mono<Reservation> persist(
            Reservation created,
            List<RequestedService> requested,
            String correlationId
    ) {
        return reservationRepository.lockTourist(created.touristId())
                .then(checkTouristLimits(created.touristId()))
                .then(lockServices(requested))
                .then(checkCapacity(created, requested))
                .then(reservationRepository.save(created))
                .flatMap(saved -> outbox.append(
                                eventFactory.reservationChanged(saved, correlationId))
                        .thenReturn(saved));
    }

    private Mono<Void> checkTouristLimits(UUID touristId) {
        Instant now = Instant.now(clock);
        return reservationRepository.countActiveByTourist(touristId, now)
                .flatMap(active -> {
                    if (active >= policy.maxActivePerTourist()) {
                        return Mono.error(new ReservationLimitException(
                                "Maximum active reservations per tourist reached: "
                                        + policy.maxActivePerTourist()));
                    }
                    return Mono.empty();
                })
                .then(reservationRepository.countCreatedSince(
                        touristId, now.minus(Duration.ofMinutes(1))))
                .flatMap(recent -> {
                    if (recent >= policy.maxCreationsPerMinute()) {
                        return Mono.error(new ReservationLimitException(
                                "Maximum reservation creations per minute reached: "
                                        + policy.maxCreationsPerMinute()));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> lockServices(List<RequestedService> requested) {
        return Flux.fromIterable(requested.stream()
                        .map(item -> item.service().serviceId())
                        .distinct()
                        .sorted()
                        .toList())
                .concatMap(reservationRepository::lockService)
                .then();
    }

    private Mono<Void> checkCapacity(Reservation created, List<RequestedService> requested) {
        return Flux.fromIterable(requested)
                .concatMap(item -> {
                    Instant from = availabilityPolicy.dayStart(created.bookedFor());
                    Instant to = availabilityPolicy.dayEnd(created.bookedFor());
                    return reservationRepository.reservedPeople(
                                    item.service().serviceId(), from, to)
                            .flatMap(reserved -> {
                                int remaining = Math.max(
                                        0, item.service().maxCapacity() - reserved);
                                if (item.reservation().people() > remaining) {
                                    return Mono.error(new ReservationConflictException(
                                            "Service " + item.service().name()
                                                    + " has only " + remaining
                                                    + " places left on "
                                                    + availabilityPolicy.serviceDate(created.bookedFor())));
                                }
                                return Mono.empty();
                            });
                })
                .then();
    }

    private void validateDraft(Reservation reservation) {
        if (reservation == null) {
            throw new InvalidCatalogException("reservation is required");
        }
        if (reservation.bookedFor() == null) {
            throw new InvalidCatalogException("reservation bookedFor is required");
        }
        if (reservation.items() == null || reservation.items().isEmpty()) {
            throw new InvalidCatalogException("reservation items are required");
        }
        if (reservation.items().size() > MAX_ITEMS) {
            throw new InvalidCatalogException(
                    "reservation cannot contain more than " + MAX_ITEMS + " items");
        }
        if (reservation.items().stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("reservation items cannot contain null values");
        }

        Set<UUID> serviceIds = new HashSet<>();
        for (ReservationItem item : reservation.items()) {
            if (item.serviceId() == null) {
                throw new InvalidCatalogException("reservation item serviceId is required");
            }
            if (item.people() < 1) {
                throw new InvalidCatalogException("reservation item people must be >= 1");
            }
            if (!serviceIds.add(item.serviceId())) {
                throw new InvalidCatalogException(
                        "reservation items cannot repeat the same service");
            }
        }
        if (reservation.notes() != null && reservation.notes().length() > 1000) {
            throw new InvalidCatalogException("reservation notes cannot exceed 1000 characters");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidCatalogException("Idempotency-Key header is required");
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidCatalogException(
                    "Idempotency-Key cannot exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return key;
    }

    private String fingerprint(UUID touristId, Reservation reservation) {
        StringBuilder canonical = new StringBuilder()
                .append(touristId).append('|')
                .append(reservation.bookedFor()).append('|')
                .append(normalize(reservation.notes())).append('|');
        reservation.items().stream()
                .sorted(Comparator.comparing(item -> item.serviceId().toString()))
                .forEach(item -> canonical.append(item.serviceId())
                        .append(':')
                        .append(item.people())
                        .append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new DomainStateException("SHA-256 is not available", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record RequestedService(int position, ReservationItem reservation, Service service) {
    }
}
