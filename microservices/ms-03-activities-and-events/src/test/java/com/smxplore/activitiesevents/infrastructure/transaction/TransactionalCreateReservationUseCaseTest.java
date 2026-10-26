package com.smxplore.activitiesevents.infrastructure.transaction;

import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionalCreateReservationUseCaseTest {

    @Mock
    private CreateReservationUseCasePort delegate;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Mock
    private ReservationRepositoryPort reservationRepo;

    private TransactionalCreateReservationUseCase useCase;

    @BeforeEach
    void setUp() {
        given(transactionalOperator.transactional(any(Mono.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        useCase = new TransactionalCreateReservationUseCase(delegate, transactionalOperator, reservationRepo);
    }

    @Test
    void returnsTheStoredReservationWhenAConcurrentRequestHitsTheUniqueIndex() {
        var reservation = reservation("checkout-123");
        var existing = reservation("checkout-123").toBuilder()
                .touristId(reservation.getTouristId())
                .build();
        given(delegate.handle(reservation))
                .willReturn(Mono.error(duplicateKey()));
        given(reservationRepo.findByTouristIdAndIdempotencyKey(reservation.getTouristId(), "checkout-123"))
                .willReturn(Mono.just(existing));

        StepVerifier.create(useCase.handle(reservation))
                .expectNext(existing)
                .verifyComplete();
    }

    @Test
    void rethrowsTheIntegrityErrorWhenNoStoredReservationMatches() {
        var reservation = reservation("checkout-123");
        given(delegate.handle(reservation))
                .willReturn(Mono.error(duplicateKey()));
        given(reservationRepo.findByTouristIdAndIdempotencyKey(reservation.getTouristId(), "checkout-123"))
                .willReturn(Mono.empty());

        StepVerifier.create(useCase.handle(reservation))
                .expectError(R2dbcDataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void doesNotLookUpTheKeyWhenTheRequestHasNone() {
        var reservation = reservation(null);
        given(delegate.handle(reservation))
                .willReturn(Mono.error(duplicateKey()));

        StepVerifier.create(useCase.handle(reservation))
                .expectError(R2dbcDataIntegrityViolationException.class)
                .verify();

        verify(reservationRepo, never()).findByTouristIdAndIdempotencyKey(any(), any());
    }

    @Test
    void doesNotReplayIntegrityErrorsOtherThanADuplicateKey() {
        var reservation = reservation("checkout-123");
        given(delegate.handle(reservation))
                .willReturn(Mono.error(new R2dbcDataIntegrityViolationException(
                        "check constraint", "23514", 0)));

        StepVerifier.create(useCase.handle(reservation))
                .expectError(R2dbcDataIntegrityViolationException.class)
                .verify();

        verify(reservationRepo, never()).findByTouristIdAndIdempotencyKey(any(), any());
    }

    private R2dbcDataIntegrityViolationException duplicateKey() {
        return new R2dbcDataIntegrityViolationException("duplicate idempotency key", "23505", 0);
    }

    private Reservation reservation(String idempotencyKey) {
        return Reservation.builder()
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .numberOfPeople(2)
                .paymentMethod(PaymentMethod.CASH)
                .bookedFor(Instant.now().plusSeconds(48 * 3600))
                .build();
    }
}
