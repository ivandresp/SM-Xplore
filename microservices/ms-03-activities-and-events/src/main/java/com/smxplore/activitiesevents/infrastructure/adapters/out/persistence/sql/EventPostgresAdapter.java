package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper.EventPersistenceMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository.EventR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Flux<Event> searchByName(String name) {
        // shortcut: substring search is a sequential scan; add a pg_trgm GIN index when the catalog grows.
        return repository.findByNameContainingIgnoreCase(name).map(mapper::toDomain);
    }
}
