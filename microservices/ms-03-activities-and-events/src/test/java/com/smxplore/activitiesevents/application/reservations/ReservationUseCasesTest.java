package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.CancellationWindowClosedException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationUseCasesTest {

    @Mock
    private ReservationRepositoryPort reservationRepo;

    @Mock
    private ActivityRepositoryPort activityRepo;

    @Mock
    private EventRepositoryPort eventRepo;

    @InjectMocks
    private CreateReservationUseCase createReservationUseCase;

    @InjectMocks
    private CancelReservationUseCase cancelReservationUseCase;

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
    void createConfirmsTheReservationAndUsesTheCatalogPrice() {
        var activity = activity(new BigDecimal("50000.00"), 10);
        var reservation = reservation(activity.getId());
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));
        given(reservationRepo.sumActivePeopleForItem(activity.getId(), ReservationItemType.ACTIVITY))
                .willReturn(Mono.just(0));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReservationUseCase.handle(reservation))
                .assertNext(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                    assertThat(saved.getUnitPrice()).isEqualByComparingTo("50000.00");
                    assertThat(saved.getTotal()).isEqualByComparingTo("100000.00");
                    assertThat(saved.getGuideId()).isEqualTo(activity.getGuideId());
                })
                .verifyComplete();
    }

    @Test
    void createRejectsInvalidReservations() {
        var withoutTourist = reservation(UUID.randomUUID()).toBuilder().touristId(null).build();
        var withoutPeople = reservation(UUID.randomUUID()).toBuilder().numberOfPeople(0).build();
        var bookedInThePast = reservation(UUID.randomUUID()).toBuilder()
                .bookedFor(Instant.now().minusSeconds(3600))
                .build();
        var withoutItem = reservation(UUID.randomUUID()).toBuilder().itemId(null).build();

        StepVerifier.create(createReservationUseCase.handle(withoutTourist))
                .expectError(InvalidReservationTouristException.class)
                .verify();
        StepVerifier.create(createReservationUseCase.handle(withoutPeople))
                .expectError(InvalidNumberOfPeopleException.class)
                .verify();
        StepVerifier.create(createReservationUseCase.handle(bookedInThePast))
                .expectError(InvalidBookingDateException.class)
                .verify();
        StepVerifier.create(createReservationUseCase.handle(withoutItem))
                .expectError(InvalidReservationItemException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void createRejectsUnpublishedItems() {
        var activity = activity(new BigDecimal("50000.00"), 10).toBuilder()
                .status(ActivityStatus.DRAFT)
                .build();
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(createReservationUseCase.handle(reservation(activity.getId())))
                .expectError(InvalidReservationItemException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void createRejectsWhenThereIsNotEnoughCapacity() {
        var activity = activity(new BigDecimal("50000.00"), 10);
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));
        given(reservationRepo.sumActivePeopleForItem(activity.getId(), ReservationItemType.ACTIVITY))
                .willReturn(Mono.just(9));

        StepVerifier.create(createReservationUseCase.handle(reservation(activity.getId())))
                .expectError(InvalidNumberOfPeopleException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void touristCanCancelOutsideTheWindow() {
        var reservation = reservation(UUID.randomUUID()).toBuilder()
                .bookedFor(Instant.now().plusSeconds(48 * 3600))
                .build();
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));

        StepVerifier.create(cancelReservationUseCase.handle(reservation.getId(), false))
                .assertNext(cancelled -> assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void touristCannotCancelInsideTheWindow() {
        var reservation = reservation(UUID.randomUUID()).toBuilder()
                .bookedFor(Instant.now().plusSeconds(3600))
                .build();
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));

        StepVerifier.create(cancelReservationUseCase.handle(reservation.getId(), false))
                .expectError(CancellationWindowClosedException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void staffCanCancelInsideTheWindow() {
        var reservation = reservation(UUID.randomUUID()).toBuilder()
                .bookedFor(Instant.now().plusSeconds(3600))
                .build();
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));

        StepVerifier.create(cancelReservationUseCase.handle(reservation.getId(), true))
                .assertNext(cancelled -> assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void stateChangesRelyOnTheDomainTransitions() {
        var reservation = givenFound(reservation(ReservationStatus.CONFIRMED));

        StepVerifier.create(registerReservationAttendanceUseCase.handle(reservation.getId()))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(ReservationStatus.COMPLETED))
                .verifyComplete();

        var noShow = givenFound(reservation(ReservationStatus.CONFIRMED));
        StepVerifier.create(registerReservationNoShowUseCase.handle(noShow.getId()))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(ReservationStatus.NO_SHOW))
                .verifyComplete();
    }

    @Test
    void stateChangesFailWhenTheReservationDoesNotExist() {
        var reservationId = UUID.randomUUID();
        given(reservationRepo.findByIdForUpdate(reservationId)).willReturn(Mono.empty());
        given(reservationRepo.findById(reservationId)).willReturn(Mono.empty());

        StepVerifier.create(registerReservationAttendanceUseCase.handle(reservationId))
                .expectError(ReservationNotFoundException.class)
                .verify();
        StepVerifier.create(consultOneReservationUseCase.handle(reservationId))
                .expectError(ReservationNotFoundException.class)
                .verify();
    }

    @Test
    void consultationsDelegateToTheRepository() {
        var touristId = UUID.randomUUID();
        var reservation = reservation(ReservationStatus.CONFIRMED);
        given(reservationRepo.findAllByTouristId(touristId, 50)).willReturn(Flux.just(reservation));
        given(reservationRepo.findAllByStatus(ReservationStatus.CONFIRMED, 50)).willReturn(Flux.just(reservation));

        StepVerifier.create(consultReservationsByTouristUseCase.handle(touristId, 50))
                .expectNext(reservation)
                .verifyComplete();
        StepVerifier.create(consultReservationsByStatusUseCase.handle(ReservationStatus.CONFIRMED, 50))
                .expectNext(reservation)
                .verifyComplete();
    }

    private Reservation givenFound(Reservation reservation) {
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));
        return reservation;
    }

    private Reservation reservation(UUID itemId) {
        return Reservation.builder()
                .id(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(itemId)
                .unitPrice(new BigDecimal("50000.00"))
                .numberOfPeople(2)
                .total(new BigDecimal("100000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(ReservationStatus.CONFIRMED)
                .bookedFor(Instant.now().plusSeconds(48 * 3600))
                .build();
    }

    private Reservation reservation(ReservationStatus status) {
        return reservation(UUID.randomUUID()).toBuilder().status(status).build();
    }

    private Activity activity(BigDecimal price, int maxCapacity) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Activity.builder()
                .guideId(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .name("Snorkel")
                .category("Nautical")
                .status(ActivityStatus.PUBLISHED)
                .price(price)
                .maxCapacity(maxCapacity)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }
}
