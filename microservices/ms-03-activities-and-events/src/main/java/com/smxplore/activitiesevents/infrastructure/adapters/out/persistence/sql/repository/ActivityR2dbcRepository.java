package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ActivityEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ActivityR2dbcRepository extends ReactiveCrudRepository<ActivityEntity, UUID> {

    Flux<ActivityEntity> findAllByCategoryIgnoreCase(String category);

    @Query("SELECT * FROM actividad WHERE status = 'PUBLISHED' "
            + "AND lower(category) = lower(:category) ORDER BY name, id LIMIT :limit OFFSET :offset")
    Flux<ActivityEntity> findPagePublishedByCategory(String category, int limit, long offset);

    @Query("SELECT COUNT(*) FROM actividad WHERE status = 'PUBLISHED' "
            + "AND lower(category) = lower(:category)")
    Mono<Long> countPublishedByCategory(String category);

    Flux<ActivityEntity> findAllBySiteId(UUID siteId);

    Flux<ActivityEntity> findAllByGuideId(UUID guideId);

    @Query("SELECT * FROM actividad WHERE status = 'PUBLISHED' AND sitio_id = :siteId "
            + "ORDER BY name, id LIMIT :limit OFFSET :offset")
    Flux<ActivityEntity> findPagePublishedBySiteId(UUID siteId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM actividad WHERE status = 'PUBLISHED' AND sitio_id = :siteId")
    Mono<Long> countPublishedBySiteId(UUID siteId);

    @Query("SELECT * FROM actividad WHERE id = :id FOR UPDATE")
    Mono<ActivityEntity> findByIdForUpdate(UUID id);

    @Query("SELECT id FROM actividad WHERE sitio_id IN (:siteIds)")
    Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds);

    // Feed de exportacion (P0-1): se pagina por updated_at, id para que el orden sea estable entre
    // paginas aunque dos filas compartan marca de actualizacion.
    @Query("SELECT * FROM actividad WHERE updated_at >= :updatedSince "
            + "ORDER BY updated_at, id LIMIT :limit OFFSET :offset")
    Flux<ActivityEntity> findPageUpdatedSince(Instant updatedSince, int limit, long offset);

    @Query("SELECT * FROM actividad ORDER BY updated_at, id LIMIT :limit OFFSET :offset")
    Flux<ActivityEntity> findPageAllForExport(int limit, long offset);

    @Query("SELECT COUNT(*) FROM actividad WHERE updated_at >= :updatedSince")
    Mono<Long> countUpdatedSince(Instant updatedSince);

    @Query("SELECT COUNT(*) FROM actividad")
    Mono<Long> countAllForExport();
}
