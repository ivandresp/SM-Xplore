package com.smxplore.sitesandservices.domain;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ReservationConflictException;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void reservationUsesPendingStatusAndAggregatesItsItems() {
        Reservation reservation = reservation(item(1, "50000.00", 0));
        reservation.validate();

        assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.numberOfPeople()).isEqualTo(1);
        assertThat(reservation.total()).isEqualByComparingTo("50000.00");
        assertThat(reservation.calculatedTotal()).isEqualByComparingTo("50000.00");
    }

    @Test
    void rejectsDuplicateServicesInTheSameReservation() {
        UUID serviceId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .reservationId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .status(ReservationStatus.PENDING)
                .total(new BigDecimal("100000.00"))
                .bookedFor(NOW.plusSeconds(3600))
                .items(List.of(
                        item(serviceId, 1, "50000.00", 0),
                        item(serviceId, 1, "50000.00", 1)
                ))
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        assertThatThrownBy(reservation::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("repeat");
    }

    @Test
    void rejectsATotalThatDoesNotMatchItsItems() {
        Reservation reservation = Reservation.builder()
                .reservationId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .status(ReservationStatus.PENDING)
                .total(new BigDecimal("1.00"))
                .bookedFor(NOW.plusSeconds(3600))
                .items(List.of(item(2, "50000.00", 0)))
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        assertThatThrownBy(reservation::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("sum");
    }

    @Test
    void cancellationRecordsTheTimestampAndReason() {
        Reservation reservation = reservation(item(2, "50000.00", 0));
        Instant cancelledAt = NOW.plusSeconds(60);

        Reservation cancelled = reservation.changeStatus(
                ReservationStatus.CANCELLED, cancelledAt, "  Cambio de planes  ");
        cancelled.validate();

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(cancelled.cancellationReason()).isEqualTo("Cambio de planes");
    }

    @Test
    void rejectsAnInvalidStatusTransition() {
        Reservation reservation = reservation(item(1, "50000.00", 0));

        assertThatThrownBy(() -> reservation.changeStatus(
                ReservationStatus.COMPLETED, NOW.plusSeconds(60), null))
                .isInstanceOf(ReservationConflictException.class)
                .hasMessageContaining("cannot transition");
    }

    private Reservation reservation(ReservationItem item) {
        return Reservation.builder()
                .reservationId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .status(ReservationStatus.PENDING)
                .total(item.total())
                .bookedFor(NOW.plusSeconds(3600))
                .items(List.of(item))
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private ReservationItem item(int people, String unitPrice, int position) {
        return item(UUID.randomUUID(), people, unitPrice, position);
    }

    private ReservationItem item(UUID serviceId, int people, String unitPrice, int position) {
        BigDecimal price = new BigDecimal(unitPrice);
        return ReservationItem.builder()
                .reservationItemId(UUID.randomUUID())
                .serviceId(serviceId)
                .serviceName("Servicio")
                .providerId(UUID.randomUUID())
                .providerName("Prestadora")
                .serviceType("RESTAURANT")
                .unitPrice(price)
                .people(people)
                .total(ReservationItem.calculateTotal(price, people))
                .position(position)
                .build();
    }
}
