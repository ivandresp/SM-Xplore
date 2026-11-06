package com.smxplore.activitiesevents.application.availability;

import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConsultAvailabilityUseCaseTest {

    @Mock
    private EventRepositoryPort eventRepo;

    @Mock
    private ActivityRepositoryPort activityRepo;

    @Mock
    private ReservationRepositoryPort reservationItemRepo;

    private ConsultAvailabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConsultAvailabilityUseCase(eventRepo, activityRepo, reservationItemRepo);
    }

    @Test
    void computesAvailableSeatsForEvent() {
        var event = event(10);
        given(eventRepo.findById(event.getId())).willReturn(Mono.just(event));
        given(reservationItemRepo.sumActivePeopleForItem(event.getId(), ReservationItemType.EVENT))
                .willReturn(Mono.just(3));

        StepVerifier.create(useCase.handle(event.getId(), ReservationItemType.EVENT))
                .assertNext(availability -> {
                    assertThat(availability.maxCapacity()).isEqualTo(10);
                    assertThat(availability.reserved()).isEqualTo(3);
                    assertThat(availability.available()).isEqualTo(7);
                })
                .verifyComplete();
    }

    @Test
    void rejectsUnknownEvent() {
        var id = UUID.randomUUID();
        given(eventRepo.findById(id)).willReturn(Mono.empty());

        StepVerifier.create(useCase.handle(id, ReservationItemType.EVENT))
                .expectError(EventNotFoundException.class)
                .verify();
    }

    private Event event(int maxCapacity) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Event.builder()
                .siteId(UUID.randomUUID())
                .name("Kayak")
                .category("Nautical")
                .status(EventStatus.PUBLISHED)
                .price(new BigDecimal("150000.00"))
                .maxCapacity(maxCapacity)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }
}
