package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper.ActivityPersistenceMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository.ActivityR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityPostgresAdapter implements ActivityRepositoryPort {

    private final ActivityR2dbcRepository repository;
    private final R2dbcEntityTemplate template;
    private final ActivityPersistenceMapper mapper;

    /**
     * R2DBC has no upsert, so an explicit existence check decides between insert and update.
     * shortcut: no optimistic locking; add a @Version column when concurrent edits matter.
     */
    @Override
    public Mono<Activity> save(Activity activity) {
        var entity = mapper.toEntity(activity);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists ? template.update(entity) : template.insert(entity))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Activity> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Activity> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Flux<Activity> findAll() {
        return repository.findAll().map(mapper::toDomain);
    }

    @Override
    public Flux<Activity> findAllByCategory(String category) {
        return repository.findAllByCategoryIgnoreCase(category).map(mapper::toDomain);
    }

    @Override
    public Flux<Activity> findAllBySiteId(UUID siteId) {
        return repository.findAllBySiteId(siteId).map(mapper::toDomain);
    }
}
