package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ServiceRepositoryPort {

    Mono<Service> save(Service service);

    Mono<Service> findById(UUID serviceId);

    Mono<PageResult<Service>> findAll(ServiceFilter filter);

    Mono<PageResult<Service>> findUpdatedSince(Instant updatedSince, int page, int size);

    Flux<Service> findByIds(Collection<UUID> serviceIds);

    /**
     * Servicios que pertenecen a la prestadora/guía indicada y, adicionalmente, a
     * cualquiera de las zonas o sitios indicados. Las listas nulas o vacías no
     * restringen esa dimensión.
     */
    Flux<Service> findByScope(UUID providerId, Collection<UUID> zoneIds, Collection<UUID> siteIds);

    Mono<Void> updateZoneSnapshot(UUID zoneId, String zoneName, ZoneStatus zoneStatus);

    Mono<Void> updateSiteSnapshot(UUID siteId, String siteName);

    Mono<Void> updateCategorySnapshot(UUID categoryId, String categoryName);

    Mono<Boolean> existsById(UUID serviceId);

    /**
     * MS04-12: identificadores de los servicios publicables de un sitio, para comprobar si el
     * turista tiene una reserva completada que permita reseñar el sitio.
     */
    Flux<UUID> findIdsBySiteId(UUID siteId);
}
