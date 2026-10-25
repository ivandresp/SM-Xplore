package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ActivityEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ActivityR2dbcRepository extends ReactiveCrudRepository<ActivityEntity, UUID> {

    Flux<ActivityEntity> findAllByCategoryIgnoreCase(String category);

    Flux<ActivityEntity> findAllBySiteId(UUID siteId);
}
