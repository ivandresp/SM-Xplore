package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ActivityEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ActivityR2dbcRepository extends ReactiveCrudRepository<ActivityEntity, UUID> {

    Flux<ActivityEntity> findAllByCategoryIgnoreCase(String category);

    Flux<ActivityEntity> findAllBySiteId(UUID siteId);

    @Query("SELECT * FROM actividad WHERE id = :id FOR UPDATE")
    Mono<ActivityEntity> findByIdForUpdate(UUID id);
}
