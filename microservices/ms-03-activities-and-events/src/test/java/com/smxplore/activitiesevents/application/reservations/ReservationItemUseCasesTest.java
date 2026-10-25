package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.DifferentProvidersInReservationException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidNumberOfPeopleException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.MixedReservationItemTypesException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyReservationItemsException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReservationItemUseCasesTest {

    @Mock
    private ReservationRepositoryPort reservationRepo;

    @Mock
    private ReservationItemRepositoryPort reservationItemRepo;

    @Mock
    private ActivityRepositoryPort activityRepo;

    @Mock
    private EventRepositoryPort eventRepo;

    private AddReservationItemsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddReservationItemsUseCase(reservationRepo, reservationItemRepo, activityRepo, eventRepo);
    }

    @Test
    void activityPriceComesFromCatalogAndTotalCountsPeople() {
        var reservation = validReservation();
        var activity = activity(new BigDecimal("80000.00"), UUID.randomUUID());
        stubReservation(reservation);
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reservationItemRepo.sumActivePeopleForItem(any(), any(), any())).willReturn(Mono.just(0));
        given(reservationItemRepo.saveAll(any())).willAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var item = item(activity.getId(), ReservationItemType.ACTIVITY);

        StepVerifier.create(useCase.handle(reservation.getId(), List.of(item)))
                .assertNext(saved -> assertThat(saved.getPrice()).isEqualByComparingTo("80000.00"))
                .verifyComplete();
    }

    @Test
    void eventPriceComesFromCatalog() {
        var reservation = validReservation();
        var event = event(new BigDecimal("150000.00"));
        stubReservation(reservation);
        given(eventRepo.findById(event.getId())).willReturn(Mono.just(event));
        given(reservationItemRepo.sumActivePeopleForItem(any(), any(), any())).willReturn(Mono.just(0));
        given(reservationItemRepo.saveAll(any())).willAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        given(reservationRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var item = item(event.getId(), ReservationItemType.EVENT);

        StepVerifier.create(useCase.handle(reservation.getId(), List.of(item)))
                .assertNext(saved -> assertThat(saved.getPrice()).isEqualByComparingTo("150000.00"))
                .verifyComplete();
    }

    @Test
    void rejectsActivityWhenCapacityIsExceeded() {
        var reservation = validReservation();
        var activity = activity(new BigDecimal("80000.00"), UUID.randomUUID());
        stubReservation(reservation);
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reservationItemRepo.sumActivePeopleForItem(any(), any(), any())).willReturn(Mono.just(9));

        var item = item(activity.getId(), ReservationItemType.ACTIVITY);

        StepVerifier.create(useCase.handle(reservation.getId(), List.of(item)))
                .expectError(InvalidNumberOfPeopleException.class)
                .verify();
    }

    @Test
    void rejectsUnpublishedActivity() {
        var reservation = validReservation();
        var activity = activity(new BigDecimal("80000.00"), UUID.randomUUID()).toBuilder()
                .status(ActivityStatus.DRAFT)
                .build();
        stubReservation(reservation);
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));

        var item = item(activity.getId(), ReservationItemType.ACTIVITY);

        StepVerifier.create(useCase.handle(reservation.getId(), List.of(item)))
                .expectError(com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException.class)
                .verify();
    }

    @Test
    void rejectsActivitiesOfDifferentGuides() {
        var reservation = validReservation();
        var first = activity(new BigDecimal("80000.00"), UUID.randomUUID());
        var second = activity(new BigDecimal("90000.00"), UUID.randomUUID());
        stubReservation(reservation);
        given(activityRepo.findById(first.getId())).willReturn(Mono.just(first));
        given(activityRepo.findById(second.getId())).willReturn(Mono.just(second));

        var items = List.of(item(first.getId(), ReservationItemType.ACTIVITY),
                item(second.getId(), ReservationItemType.ACTIVITY));

        StepVerifier.create(useCase.handle(reservation.getId(), items))
                .expectError(DifferentProvidersInReservationException.class)
                .verify();
    }

    @Test
    void rejectsMoreThanOneEventItem() {
        var reservation = validReservation();
        stubReservation(reservation);

        var items = List.of(item(UUID.randomUUID(), ReservationItemType.EVENT),
                item(UUID.randomUUID(), ReservationItemType.EVENT));

        StepVerifier.create(useCase.handle(reservation.getId(), items))
                .expectError(TooManyReservationItemsException.class)
                .verify();
    }

    @Test
    void rejectsMixedItemTypes() {
        var reservation = validReservation();

        var items = List.of(item(UUID.randomUUID(), ReservationItemType.EVENT),
                item(UUID.randomUUID(), ReservationItemType.ACTIVITY));

        StepVerifier.create(useCase.handle(reservation.getId(), items))
                .expectError(MixedReservationItemTypesException.class)
                .verify();
    }

    private void stubReservation(Reservation reservation) {
        given(reservationRepo.findByIdForUpdate(reservation.getId())).willReturn(Mono.just(reservation));
        given(reservationItemRepo.findByReservationId(reservation.getId())).willReturn(Flux.empty());
    }

    private Reservation validReservation() {
        return Reservation.builder()
                .touristId(UUID.randomUUID())
                .numberOfPeople(2)
                .paymentMethod(PaymentMethod.CARD)
                .bookedFor(Instant.now().plusSeconds(86_400))
                .status(ReservationStatus.PENDING)
                .total(BigDecimal.ZERO)
                .build();
    }

    private ReservationItem item(UUID itemId, ReservationItemType type) {
        return ReservationItem.builder()
                .itemId(itemId)
                .type(type)
                .price(BigDecimal.ZERO)
                .build();
    }

    private Activity activity(BigDecimal price, UUID guideId) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Activity.builder()
                .siteId(UUID.randomUUID())
                .guideId(guideId)
                .name("Snorkel")
                .category("Water")
                .status(ActivityStatus.PUBLISHED)
                .price(price)
                .maxCapacity(10)
                .durationMinutes(60)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }

    private Event event(BigDecimal price) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Event.builder()
                .siteId(UUID.randomUUID())
                .name("Kayak Tour")
                .category("Nautical")
                .status(EventStatus.PUBLISHED)
                .price(price)
                .maxCapacity(20)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }
}
