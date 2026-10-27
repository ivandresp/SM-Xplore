package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ServiceRepositoryPort {

    Mono<Service> save(Service service);

    Mono<Service> findById(UUID serviceId);

    Mono<PageResult<Service>> findAll(ServiceFilter filter);

    Mono<Void> updateZoneSnapshot(UUID zoneId, String zoneName, ZoneStatus zoneStatus);

    Mono<Void> updateCategorySnapshot(UUID categoryId, String categoryName);

    Mono<Boolean> existsById(UUID serviceId);
}
