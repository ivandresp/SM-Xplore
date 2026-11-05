package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper.EventPersistenceMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository.EventR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EventPostgresAdapter implements EventRepositoryPort {

    private final EventR2dbcRepository repository;
    private final R2dbcEntityTemplate template;
    private final EventPersistenceMapper mapper;

    /**
     * R2DBC has no upsert, so an explicit existence check decides between insert and update.
     * shortcut: no optimistic locking; add a @Version column when concurrent edits matter.
     */
    @Override
    public Mono<Event> save(Event event) {
        var entity = mapper.toEntity(event);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists ? template.update(entity) : template.insert(entity))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Event> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Event> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Flux<Event> findAll() {
        return repository.findAll().map(mapper::toDomain);
    }

    @Override
    public Flux<Event> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        return repository.findAllById(ids).map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Event>> findPublishedPage(int page, int size) {
        var offset = (long) page * size;
        return Mono.zip(
                        repository.findPagePublished(size, offset).map(mapper::toDomain).collectList(),
                        repository.countPublished().defaultIfEmpty(0L))
                .map(result -> new PageResult<>(result.getT1(), page, size, result.getT2()));
    }

    @Override
    public Flux<Event> searchByName(String name) {
        // shortcut: substring search is a sequential scan; add a pg_trgm GIN index when the catalog grows.
        return repository.findByNameContainingIgnoreCase(name).map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Event>> searchPublishedByNamePage(String name, int page, int size) {
        var offset = (long) page * size;
        return Mono.zip(
                        repository.searchPagePublishedByName(name, size, offset)
                                .map(mapper::toDomain)
                                .collectList(),
                        repository.countSearchPublishedByName(name).defaultIfEmpty(0L))
                .map(result -> new PageResult<>(result.getT1(), page, size, result.getT2()));
    }

    @Override
    public Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds) {
        return repository.findIdsBySiteIds(siteIds);
    }

    @Override
    public Mono<Integer> applySiteSnapshot(UUID siteId, String siteName, UUID managerId, Instant siteUpdatedAt) {
        return template.getDatabaseClient()
                .sql("""
                        UPDATE evento
                        SET sitio_nombre = COALESCE(:siteName, sitio_nombre),
                            manager_id = :managerId,
                            sitio_actualizado_en = :siteUpdatedAt
                        WHERE sitio_id = :siteId
                          AND (sitio_actualizado_en IS NULL OR sitio_actualizado_en < :siteUpdatedAt)
                        """)
                .bind("siteName", siteName)
                .bind("managerId", managerId)
                .bind("siteUpdatedAt", siteUpdatedAt)
                .bind("siteId", siteId)
                .fetch().rowsUpdated()
                .map(Long::intValue);
    }
}
