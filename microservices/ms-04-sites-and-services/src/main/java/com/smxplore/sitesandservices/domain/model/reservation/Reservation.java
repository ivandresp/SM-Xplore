package com.smxplore.sitesandservices.domain.model.reservation;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ReservationConflictException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Reservation {

    private final UUID reservationId;
    private final UUID touristId;
    private final String touristName;
    @Builder.Default
    private final ReservationStatus status = ReservationStatus.PENDING;
    private final BigDecimal total;
    private final Instant bookedFor;
    private final String notes;
    @Builder.Default
    private final List<ReservationItem> items = List.of();
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant confirmedAt;
    private final Instant cancelledAt;
    private final String cancellationReason;
    private final String idempotencyKey;
    private final String requestFingerprint;

    public void validate() {
        validateReservationId(reservationId);
        validateTourist(touristId, touristName);
        validateStatus(status);
        validateBookedFor(bookedFor);
        validateItems(items);
        validateTotal(total, items);
        validateNotes(notes);
        validateCancellationReason(cancellationReason);
        validateIdempotency(idempotencyKey, requestFingerprint);
        validateCreatedAt(createdAt);
        validateUpdatedAt(updatedAt);
        validateChronology(createdAt, updatedAt);
        validateLifecycleTimestamps(status, createdAt, updatedAt, confirmedAt, cancelledAt);
    }

    public int numberOfPeople() {
        return items.stream().mapToInt(ReservationItem::people).sum();
    }

    public BigDecimal calculatedTotal() {
        return items.stream()
                .map(ReservationItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public boolean belongsToTourist(UUID touristId) {
        return this.touristId.equals(touristId);
    }

    public boolean belongsToProvider(UUID providerId) {
        return items.stream().anyMatch(item -> item.providerId().equals(providerId));
    }

    public boolean belongsExclusivelyToProvider(UUID providerId) {
        return !items.isEmpty()
                && items.stream().allMatch(item -> item.providerId().equals(providerId));
    }

    public Reservation changeStatus(ReservationStatus next, Instant now, String reason) {
        if (next == null) {
            throw new InvalidCatalogException("reservation status is required");
        }
        if (next == status) {
            return this;
        }
        if (!status.canTransitionTo(next)) {
            throw new ReservationConflictException(
                    "Reservation " + reservationId + " cannot transition from "
                            + status + " to " + next);
        }
        ReservationBuilder builder = toBuilder()
                .status(next)
                .updatedAt(now);
        if (next == ReservationStatus.CONFIRMED) {
            builder.confirmedAt(now);
        }
        if (next == ReservationStatus.CANCELLED) {
            builder.cancelledAt(now)
                    .cancellationReason(normalizeReason(reason));
        }
        return builder.build();
    }

    private static void validateReservationId(UUID reservationId) {
        if (reservationId == null) {
            throw new InvalidCatalogException("reservationId is required");
        }
    }

    private static void validateTourist(UUID touristId, String touristName) {
        if (touristId == null) {
            throw new InvalidCatalogException("reservation touristId is required");
        }
        if (touristName == null || touristName.isBlank()) {
            throw new InvalidCatalogException("reservation touristName is required");
        }
    }

    private static void validateStatus(ReservationStatus status) {
        if (status == null) {
            throw new InvalidCatalogException("reservation status is required");
        }
    }

    private static void validateBookedFor(Instant bookedFor) {
        if (bookedFor == null) {
            throw new InvalidCatalogException("reservation bookedFor is required");
        }
    }

    private static void validateItems(List<ReservationItem> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidCatalogException("reservation items are required");
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("reservation items cannot contain null values");
        }
        Set<UUID> serviceIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (ReservationItem item : items) {
            item.validate();
            if (!serviceIds.add(item.serviceId())) {
                throw new InvalidCatalogException(
                        "reservation items cannot repeat the same service");
            }
            if (!positions.add(item.position())) {
                throw new InvalidCatalogException(
                        "reservation items cannot repeat the same position");
            }
        }
        // La regla de una sola prestadora se aplica a las reservas nuevas en
        // ReservationCreateService. No se valida aquí para poder leer datos
        // históricos creados antes de esa restricción.
    }

    private static void validateTotal(BigDecimal total, List<ReservationItem> items) {
        if (total == null) {
            throw new InvalidCatalogException("reservation total is required");
        }
        if (total.signum() < 0) {
            throw new InvalidCatalogException("reservation total must be >= 0");
        }
        BigDecimal expected = items.stream()
                .map(ReservationItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(expected) != 0) {
            throw new InvalidCatalogException(
                    "reservation total must equal the sum of its items");
        }
    }

    private static void validateNotes(String notes) {
        if (notes != null && notes.length() > 1000) {
            throw new InvalidCatalogException("reservation notes cannot exceed 1000 characters");
        }
    }

    private static void validateCancellationReason(String cancellationReason) {
        if (cancellationReason != null && cancellationReason.length() > 500) {
            throw new InvalidCatalogException(
                    "reservation cancellationReason cannot exceed 500 characters");
        }
    }

    private static void validateIdempotency(String idempotencyKey, String requestFingerprint) {
        if (idempotencyKey == null) {
            return;
        }
        if (idempotencyKey.isBlank()) {
            throw new InvalidCatalogException("reservation idempotencyKey cannot be blank");
        }
        if (idempotencyKey.length() > 100) {
            throw new InvalidCatalogException(
                    "reservation idempotencyKey cannot exceed 100 characters");
        }
        if (requestFingerprint == null || requestFingerprint.length() != 64) {
            throw new InvalidCatalogException(
                    "reservation requestFingerprint must be a SHA-256 hex digest");
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidCatalogException("reservation createdAt is required");
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new InvalidCatalogException("reservation updatedAt is required");
        }
    }

    private static void validateChronology(Instant createdAt, Instant updatedAt) {
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidCatalogException("reservation updatedAt cannot be before createdAt");
        }
    }

    private static void validateLifecycleTimestamps(
            ReservationStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt,
            Instant cancelledAt
    ) {
        validateLifecycleTimestamp("confirmedAt", confirmedAt, createdAt, updatedAt);
        validateLifecycleTimestamp("cancelledAt", cancelledAt, createdAt, updatedAt);
        if ((status == ReservationStatus.CONFIRMED || status == ReservationStatus.COMPLETED)
                && confirmedAt == null) {
            throw new InvalidCatalogException(
                    "reservation confirmedAt is required when status is " + status);
        }
        if (status == ReservationStatus.CANCELLED && cancelledAt == null) {
            throw new InvalidCatalogException(
                    "reservation cancelledAt is required when status is CANCELLED");
        }
    }

    private static void validateLifecycleTimestamp(
            String field,
            Instant value,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (value == null) {
            return;
        }
        if (value.isBefore(createdAt)) {
            throw new InvalidCatalogException(
                    "reservation " + field + " cannot be before createdAt");
        }
        if (value.isAfter(updatedAt)) {
            throw new InvalidCatalogException(
                    "reservation " + field + " cannot be after updatedAt");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
