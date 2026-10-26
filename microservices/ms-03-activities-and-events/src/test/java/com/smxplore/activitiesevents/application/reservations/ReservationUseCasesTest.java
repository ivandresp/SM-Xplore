package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.CancellationWindowClosedException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidBookingDateException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationTouristException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyActiveReservationsException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
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

    @BeforeEach
    void allowTouristLock() {
        lenient().when(reservationRepo.lockTourist(any())).thenReturn(Mono.empty());
    }

    @Test
    void createConfirmsTheReservationAndUsesTheCatalogPriceAndStart() {
        var activity = activity(new BigDecimal("50000.00"), 10);
        var reservation = reservation(activity.getId());
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));
        given(reservationRepo.sumActivePeopleForItem(activity.getId(), ReservationItemType.ACTIVITY))
                .willReturn(Mono.just(0));
        given(reservationRepo.countActiveByTourist(any())).willReturn(Mono.just(0L));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReservationUseCase.handle(reservation))
                .assertNext(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                    assertThat(saved.getUnitPrice()).isEqualByComparingTo("50000.00");
                    assertThat(saved.getTotal()).isEqualByComparingTo("100000.00");
                    assertThat(saved.getBookedFor()).isEqualTo(activity.getStartsAt());
                    assertThat(saved.getItemStartsAt()).isEqualTo(activity.getStartsAt());
                    assertThat(saved.getGuideId()).isEqualTo(activity.getGuideId());
                })
                .verifyComplete();
    }

    @Test
    void createSupportsEventsAndCopiesTheManager() {
        var event = event(new BigDecimal("30000.00"), 4);
        var reservation = reservationItem(event.getId(), ReservationItemType.EVENT);
        given(eventRepo.findByIdForUpdate(event.getId())).willReturn(Mono.just(event));
        given(reservationRepo.sumActivePeopleForItem(event.getId(), ReservationItemType.EVENT))
                .willReturn(Mono.just(0));
        given(reservationRepo.countActiveByTourist(any())).willReturn(Mono.just(0L));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReservationUseCase.handle(reservation))
                .assertNext(saved -> {
                    assertThat(saved.getTotal()).isEqualByComparingTo("60000.00");
                    assertThat(saved.getProviderId()).isEqualTo(event.getManagerId());
                    assertThat(saved.getGuideId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void createAcceptsTheExactCapacity() {
        var activity = activity(new BigDecimal("50000.00"), 10);
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));
        given(reservationRepo.sumActivePeopleForItem(activity.getId(), ReservationItemType.ACTIVITY))
                .willReturn(Mono.just(8));
        given(reservationRepo.countActiveByTourist(any())).willReturn(Mono.just(0L));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReservationUseCase.handle(reservation(activity.getId())))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED))
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
    void createRejectsItemsThatAlreadyStarted() {
        var activity = activity(new BigDecimal("50000.00"), 10).toBuilder()
                .startsAt(Instant.now().minusSeconds(600))
                .endsAt(Instant.now().plusSeconds(3_600))
                .build();
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(createReservationUseCase.handle(reservation(activity.getId())))
                .expectError(InvalidBookingDateException.class)
                .verify();
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
    void createRejectsTouristsWithTooManyActiveReservations() {
        var activity = activity(new BigDecimal("50000.00"), 10);
        given(activityRepo.findByIdForUpdate(activity.getId())).willReturn(Mono.just(activity));
        given(reservationRepo.sumActivePeopleForItem(activity.getId(), ReservationItemType.ACTIVITY))
                .willReturn(Mono.just(0));
        given(reservationRepo.countActiveByTourist(any())).willReturn(Mono.just(5L));

        StepVerifier.create(createReservationUseCase.handle(reservation(activity.getId())))
                .expectError(TooManyActiveReservationsException.class)
                .verify();
    }

    @Test
    void touristCanCancelOutsideTheWindow() {
        var reservation = reservation(UUID.randomUUID()).toBuilder()
                .itemStartsAt(Instant.now().plusSeconds(48 * 3600))
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
                .itemStartsAt(Instant.now().plusSeconds(3600))
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
                .itemStartsAt(Instant.now().plusSeconds(3600))
                .build();
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));

        StepVerifier.create(cancelReservationUseCase.handle(reservation.getId(), true))
                .assertNext(cancelled -> assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void stateChangesRelyOnTheDomainTransitions() {
        var reservation = givenFound(startedReservation());
        StepVerifier.create(registerReservationAttendanceUseCase.handle(reservation.getId()))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(ReservationStatus.COMPLETED))
                .verifyComplete();

        var noShow = givenFound(startedReservation());
        StepVerifier.create(registerReservationNoShowUseCase.handle(noShow.getId()))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(ReservationStatus.NO_SHOW))
                .verifyComplete();
    }

    @Test
    void attendanceCannotBeRegisteredBeforeTheItemStarts() {
        var reservation = givenLocked(reservation(UUID.randomUUID()).toBuilder()
                .itemStartsAt(Instant.now().plusSeconds(3600))
                .build());

        StepVerifier.create(registerReservationAttendanceUseCase.handle(reservation.getId()))
                .expectError(InvalidReservationStatusTransitionException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
    }

    @Test
    void terminalStatesCannotChangeAgain() {
        var reservation = givenLocked(startedReservation().toBuilder()
                .status(ReservationStatus.CANCELLED)
                .build());

        StepVerifier.create(registerReservationAttendanceUseCase.handle(reservation.getId()))
                .expectError(InvalidReservationStatusTransitionException.class)
                .verify();

        verify(reservationRepo, never()).save(any());
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

    private Reservation givenLocked(Reservation reservation) {
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        return reservation;
    }

    private Reservation givenFound(Reservation reservation) {
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationRepo.save(reservation)).willReturn(Mono.just(reservation));
        return reservation;
    }

    private Reservation startedReservation() {
        return reservation(ReservationStatus.CONFIRMED).toBuilder()
                .itemStartsAt(Instant.now().minusSeconds(600))
                .build();
    }

    private Reservation reservation(UUID itemId) {
        return reservationItem(itemId, ReservationItemType.ACTIVITY);
    }

    private Reservation reservationItem(UUID itemId, ReservationItemType itemType) {
        return Reservation.builder()
                .id(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .itemType(itemType)
                .itemId(itemId)
                .numberOfPeople(2)
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

    private Event event(BigDecimal price, int maxCapacity) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Event.builder()
                .managerId(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .name("Festival")
                .category("Music")
                .status(EventStatus.PUBLISHED)
                .price(price)
                .maxCapacity(maxCapacity)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }
}
