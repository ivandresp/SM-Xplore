package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves the manager's sites through ms-04, then the events/activities of those sites, and
 * finally one page of the reservations of those items. The database applies the offset and
 * returns the total, so a manager with a big catalog never loads it whole.
 */
@RequiredArgsConstructor
public class ConsultManagerReservationsUseCase {

    private final SiteCatalogPort siteCatalog;
    private final EventRepositoryPort eventRepository;
    private final ActivityRepositoryPort activityRepository;
    private final ReservationRepositoryPort reservationRepository;

    public Mono<PageResult<Reservation>> handle(UUID managerId, String bearerToken, ReservationStatus status,
                                                int page, int size) {
        return siteCatalog.findByManagerId(managerId, bearerToken)
                .map(SiteCatalogPort.SiteSnapshot::siteId)
                .collectList()
                .flatMap(siteIds -> {
                    if (siteIds.isEmpty()) {
                        return Mono.just(PageResult.empty(page, size));
                    }
                    return Mono.zip(
                                    eventRepository.findIdsBySiteIds(siteIds).collectList(),
                                    activityRepository.findIdsBySiteIds(siteIds).collectList())
                            .flatMap(items -> {
                                var eventIds = items.getT1();
                                var activityIds = items.getT2();
                                if (eventIds.isEmpty() && activityIds.isEmpty()) {
                                    return Mono.just(PageResult.<Reservation>empty(page, size));
                                }
                                return reservationRepository.findPageByItemRefs(
                                        eventIds, activityIds, status, page, size);
                            });
                });
    }
}
