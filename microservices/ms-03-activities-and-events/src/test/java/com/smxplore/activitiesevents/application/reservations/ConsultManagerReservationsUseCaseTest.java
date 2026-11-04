package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ConsultManagerReservationsUseCaseTest {

    private final SiteCatalogPort siteCatalog = mock(SiteCatalogPort.class);
    private final EventRepositoryPort eventRepository = mock(EventRepositoryPort.class);
    private final ActivityRepositoryPort activityRepository = mock(ActivityRepositoryPort.class);
    private final ReservationRepositoryPort reservationRepository = mock(ReservationRepositoryPort.class);
    private final ConsultManagerReservationsUseCase useCase = new ConsultManagerReservationsUseCase(
            siteCatalog, eventRepository, activityRepository, reservationRepository);

    @Test
    void managerWithoutSitesGetsAnEmptyListWithoutQueryingItems() {
        var managerId = UUID.randomUUID();
        given(siteCatalog.findByManagerId(managerId, "token")).willReturn(Flux.empty());

        StepVerifier.create(useCase.handle(managerId, "token", ReservationStatus.CONFIRMED, 50))
                .verifyComplete();

        verifyNoInteractions(eventRepository, activityRepository, reservationRepository);
    }

    @Test
    void managerGetsOnlyReservationsOfTheirSitesItems() {
        var managerId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var reservation = mock(Reservation.class);
        given(siteCatalog.findByManagerId(managerId, "token"))
                .willReturn(Flux.just(new SiteCatalogPort.SiteSnapshot(siteId, managerId, "Site")));
        given(eventRepository.findIdsBySiteIds(any())).willReturn(Flux.just(eventId));
        given(activityRepository.findIdsBySiteIds(any())).willReturn(Flux.empty());
        given(reservationRepository.findAllByItemRefs(any(), any(), eq(ReservationStatus.CONFIRMED), eq(50)))
                .willReturn(Flux.just(reservation));

        StepVerifier.create(useCase.handle(managerId, "token", ReservationStatus.CONFIRMED, 50))
                .expectNext(reservation)
                .verifyComplete();
    }

    @Test
    void sitesWithoutItemsReturnAnEmptyList() {
        var managerId = UUID.randomUUID();
        given(siteCatalog.findByManagerId(managerId, "token"))
                .willReturn(Flux.just(new SiteCatalogPort.SiteSnapshot(UUID.randomUUID(), managerId, "Site")));
        given(eventRepository.findIdsBySiteIds(any())).willReturn(Flux.empty());
        given(activityRepository.findIdsBySiteIds(any())).willReturn(Flux.empty());

        StepVerifier.create(useCase.handle(managerId, "token", ReservationStatus.CONFIRMED, 50))
                .verifyComplete();
    }
}
