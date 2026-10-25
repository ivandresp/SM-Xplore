package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationUseCasesTest {

    @Mock
    private ReservationRepositoryPort reservationRepo;

    @InjectMocks
    private CreateReservationUseCase createReservationUseCase;

    @InjectMocks
    private ConfirmReservationUseCase confirmReservationUseCase;

    @InjectMocks
    private RejectReservationUseCase rejectReservationUseCase;

    @InjectMocks
    private CancelReservationUseCase cancelReservationUseCase;

    @InjectMocks
    private RequestReservationCancellationUseCase requestReservationCancellationUseCase;

    @InjectMocks
    private RegisterReservationAttendanceUseCase registerReservationAttendanceUseCase;

    @InjectMocks
    private RegisterReservationNoShowUseCase registerReservationNoShowUseCase;

    @InjectMocks
    private ConsultOneReservationUseCase consultOneReservationUseCase;

    @InjectMocks
    private ConsultReservationsByStatusUseCase consultReservationsByStatusUseCase;

    @InjectMocks
    private ConsultReservationsByTouristUseCase consultReservationsByTouristUseCase;

    @Test
    void createSavesAValidReservation() {
        var reservation = reservation(ReservationStatus.PENDING);
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));

        StepVerifier.create(createReservationUseCase.handle(reservation))
                .assertNext(saved -> assertThat(saved.getId()).isEqualTo(reservation.getId()))
                .verifyComplete();

        verify(reservationRepo).save(reservation);
    }

    @Test
    void createRejectsInvalidReservations() {
        var withoutTourist = Reservation.builder()
                .numberOfPeople(2)
                .total(new BigDecimal("100.00"))
                .bookedFor(Instant.now().plusSeconds(3600))
                .build();
        var withoutPeople = reservation(ReservationStatus.PENDING).toBuilder()
                .numberOfPeople(0)
                .build();
        var bookedInThePast = reservation(ReservationStatus.PENDING).toBuilder()
                .bookedFor(Instant.now().minusSeconds(3600))
                .build();

        StepVerifier.create(createReservationUseCase.handle(withoutTourist))
                .expectError(InvalidReservationTouristException.class)
                .verify();
        StepVerifier.create(createReservationUseCase.handle(withoutPeople))
                .expectError(InvalidNumberOfPeopleException.class)
                .verify();
        StepVerifier.create(createReservationUseCase.handle(bookedInThePast))
                .expectError(InvalidBookingDateException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void confirmMovesAPendingReservationToConfirmed() {
        var reservation = reservation(ReservationStatus.PENDING);
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));

        StepVerifier.create(confirmReservationUseCase.handle(reservation.getId()))
                .assertNext(confirmed -> assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();
    }

    @Test
    void stateChangesRelyOnTheDomainTransitions() {
        assertTransition(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, cancelReservationUseCase::handle);
        assertTransition(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLATION_REQUESTED,
                requestReservationCancellationUseCase::handle);
        assertTransition(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED,
                registerReservationAttendanceUseCase::handle);
        assertTransition(ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW, registerReservationNoShowUseCase::handle);
        assertTransition(ReservationStatus.PENDING, ReservationStatus.REJECTED, rejectReservationUseCase::handle);
    }

    @Test
    void rejectIsRejectedWhenTheReservationIsAlreadyConfirmed() {
        var reservation = reservation(ReservationStatus.CONFIRMED);
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));

        StepVerifier.create(rejectReservationUseCase.handle(reservation.getId()))
                .expectError(InvalidReservationStatusTransitionException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void stateChangesFailWhenTheReservationDoesNotExist() {
        var reservationId = UUID.randomUUID();
        given(reservationRepo.findByIdForUpdate(reservationId)).willReturn(Mono.empty());
        given(reservationRepo.findById(reservationId)).willReturn(Mono.empty());

        StepVerifier.create(confirmReservationUseCase.handle(reservationId))
                .expectError(ReservationNotFoundException.class)
                .verify();
        StepVerifier.create(consultOneReservationUseCase.handle(reservationId))
                .expectError(ReservationNotFoundException.class)
                .verify();
    }

    @Test
    void consultationsDelegateToTheRepository() {
        var touristId = UUID.randomUUID();
        var reservation = reservation(ReservationStatus.PENDING);
        given(reservationRepo.findAllByTouristId(touristId, 50)).willReturn(Flux.just(reservation));
        given(reservationRepo.findAllByStatus(ReservationStatus.PENDING, 50)).willReturn(Flux.just(reservation));

        StepVerifier.create(consultReservationsByTouristUseCase.handle(touristId, 50))
                .expectNext(reservation)
                .verifyComplete();
        StepVerifier.create(consultReservationsByStatusUseCase.handle(ReservationStatus.PENDING, 50))
                .expectNext(reservation)
                .verifyComplete();
    }

    private void assertTransition(ReservationStatus from, ReservationStatus to,
                                  Function<UUID, Mono<Reservation>> useCase) {
        var reservation = givenFound(reservation(from));

        StepVerifier.create(useCase.apply(reservation.getId()))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(to))
                .verifyComplete();
    }

    private Reservation givenFound(Reservation reservation) {
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));
        return reservation;
    }

    private Reservation reservation(ReservationStatus status) {
        return Reservation.builder()
                .id(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .numberOfPeople(2)
                .total(new BigDecimal("150000.00"))
                .paymentMethod(PaymentMethod.CARD)
                .status(status)
                .bookedFor(Instant.now().plusSeconds(3600))
                .build();
    }
}
