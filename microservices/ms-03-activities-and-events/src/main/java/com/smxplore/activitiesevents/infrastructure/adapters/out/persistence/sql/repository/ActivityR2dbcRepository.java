package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ActivityEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface ActivityR2dbcRepository extends ReactiveCrudRepository<ActivityEntity, UUID> {

    Flux<ActivityEntity> findAllByCategoryIgnoreCase(String category);

    @Query("SELECT * FROM actividad WHERE status = 'PUBLISHED' ORDER BY name, id")
    Flux<ActivityEntity> findPublished();

    @Query("SELECT * FROM actividad WHERE status = 'PUBLISHED' "
            + "AND lower(category) = lower(:category) ORDER BY name, id")
    Flux<ActivityEntity> findPublishedByCategory(String category);

    Flux<ActivityEntity> findAllBySiteId(UUID siteId);

    @Query("SELECT * FROM actividad WHERE status = 'PUBLISHED' AND sitio_id = :siteId ORDER BY name, id")
    Flux<ActivityEntity> findPublishedBySiteId(UUID siteId);

    @Query("SELECT * FROM actividad WHERE id = :id FOR UPDATE")
    Mono<ActivityEntity> findByIdForUpdate(UUID id);

    @Query("SELECT id FROM actividad WHERE sitio_id IN (:siteIds)")
    Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds);
}
