package com.smxplore.activitiesevents.infrastructure.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.application.outbox.ReservationEventFactory;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationCloseCandidate;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiredReservationCleanupJobTest {

    @Mock
    private ReservationRepositoryPort reservationRepo;
    @Mock
    private OutboxPort outbox;

    @Test
    void closesStaleReservationsAndPublishesTheChange() {
        var grace = Duration.ofHours(24);
        var job = new ExpiredReservationCleanupJob(reservationRepo, outbox,
                new ReservationEventFactory(new ObjectMapper().findAndRegisterModules()),
                passThroughTransactions(), grace);
        var before = Instant.now().minus(grace);
        var reservation = staleReservation();
        var candidate = new ReservationCloseCandidate(reservation.getId(), ReservationStatus.NO_SHOW);
        given(reservationRepo.findStaleConfirmed(any())).willReturn(Flux.just(candidate));
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        given(outbox.enqueue(any())).willReturn(Mono.empty());

        StepVerifier.create(job.closeExpired()).verifyComplete();

        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(reservationRepo).findStaleConfirmed(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                before.minusSeconds(1),
                Instant.now().minus(grace).plusSeconds(1));
        then(reservationRepo).should().save(org.mockito.ArgumentMatchers.argThat(
                saved -> saved.getStatus() == ReservationStatus.NO_SHOW));
        then(outbox).should().enqueue(org.mockito.ArgumentMatchers.argThat(
                event -> event.eventVersion() == 2
                        && event.payload().contains("\"status\":\"NO_SHOW\"")));
    }

    @Test
    void rejectsNonPositiveGrace() {
        assertThatThrownBy(() -> new ExpiredReservationCleanupJob(reservationRepo, outbox,
                new ReservationEventFactory(new ObjectMapper()), passThroughTransactions(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Sin base de datos no hay transaccion real: el operador devuelve el mismo flujo. */
    @SuppressWarnings("unchecked")
    private static org.springframework.transaction.reactive.TransactionalOperator passThroughTransactions() {
        var operator = org.mockito.Mockito.mock(
                org.springframework.transaction.reactive.TransactionalOperator.class);
        lenient().when(operator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return operator;
    }

    private static Reservation staleReservation() {
        var start = Instant.now().minus(Duration.ofDays(2));
        return Reservation.builder()
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(UUID.randomUUID())
                .itemName("Snorkel")
                .itemStartsAt(start)
                .bookedFor(start)
                .unitPrice(new BigDecimal("100.00"))
                .numberOfPeople(2)
                .total(new BigDecimal("200.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(ReservationStatus.CONFIRMED)
                .guideId(UUID.randomUUID())
                .build();
    }
}