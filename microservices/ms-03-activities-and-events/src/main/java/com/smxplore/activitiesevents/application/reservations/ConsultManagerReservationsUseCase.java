package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves the manager's sites through ms-04, then the events/activities of
 * those sites, and finally the reservations of those items.
 */
@RequiredArgsConstructor
public class ConsultManagerReservationsUseCase {

    private final SiteCatalogPort siteCatalog;
    private final EventRepositoryPort eventRepository;
    private final ActivityRepositoryPort activityRepository;
    private final ReservationRepositoryPort reservationRepository;

    public Flux<Reservation> handle(UUID managerId, String bearerToken, ReservationStatus status, int limit) {
        return siteCatalog.findByManagerId(managerId, bearerToken)
                .map(SiteCatalogPort.SiteSnapshot::siteId)
                .collectList()
                .flatMapMany(siteIds -> {
                    if (siteIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return Mono.zip(
                                    eventRepository.findIdsBySiteIds(siteIds).collectList(),
                                    activityRepository.findIdsBySiteIds(siteIds).collectList())
                            .flatMapMany(items -> {
                                var eventIds = items.getT1();
                                var activityIds = items.getT2();
                                if (eventIds.isEmpty() && activityIds.isEmpty()) {
                                    return Flux.empty();
                                }
                                return reservationRepository.findAllByItemRefs(eventIds, activityIds, status, limit);
                            });
                });
    }
}
