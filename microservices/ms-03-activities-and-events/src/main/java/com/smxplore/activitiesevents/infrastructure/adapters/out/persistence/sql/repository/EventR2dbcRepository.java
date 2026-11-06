package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.EventEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface EventR2dbcRepository extends ReactiveCrudRepository<EventEntity, UUID> {

    Flux<EventEntity> findByNameContainingIgnoreCase(String name);

    @Query("SELECT * FROM evento WHERE status = 'PUBLISHED' "
            + "ORDER BY starts_at, id LIMIT :limit OFFSET :offset")
    Flux<EventEntity> findPagePublished(int limit, long offset);

    @Query("SELECT COUNT(*) FROM evento WHERE status = 'PUBLISHED'")
    Mono<Long> countPublished();

    @Query("SELECT * FROM evento WHERE status = 'PUBLISHED' "
            + "AND name ILIKE '%' || :name || '%' ORDER BY starts_at, id LIMIT :limit OFFSET :offset")
    Flux<EventEntity> searchPagePublishedByName(String name, int limit, long offset);

    @Query("SELECT COUNT(*) FROM evento WHERE status = 'PUBLISHED' "
            + "AND name ILIKE '%' || :name || '%'")
    Mono<Long> countSearchPublishedByName(String name);

    @Query("SELECT * FROM evento WHERE id = :id FOR UPDATE")
    Mono<EventEntity> findByIdForUpdate(UUID id);

    @Query("SELECT id FROM evento WHERE sitio_id IN (:siteIds)")
    Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds);

    // Feed de exportacion (P0-1): se pagina por updated_at, id para que el orden sea estable entre
    // paginas aunque dos filas compartan marca de actualizacion.
    @Query("SELECT * FROM evento WHERE updated_at >= :updatedSince "
            + "ORDER BY updated_at, id LIMIT :limit OFFSET :offset")
    Flux<EventEntity> findPageUpdatedSince(Instant updatedSince, int limit, long offset);

    @Query("SELECT * FROM evento ORDER BY updated_at, id LIMIT :limit OFFSET :offset")
    Flux<EventEntity> findPageAllForExport(int limit, long offset);

    @Query("SELECT COUNT(*) FROM evento WHERE updated_at >= :updatedSince")
    Mono<Long> countUpdatedSince(Instant updatedSince);

    @Query("SELECT COUNT(*) FROM evento")
    Mono<Long> countAllForExport();
}
