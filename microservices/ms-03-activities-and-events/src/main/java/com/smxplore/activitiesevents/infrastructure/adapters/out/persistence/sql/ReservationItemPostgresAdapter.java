package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ReservationItemEntity;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper.ReservationItemPersistenceMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository.ReservationItemR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.stream.StreamSupport;

@Repository
@RequiredArgsConstructor
public class ReservationItemPostgresAdapter implements ReservationItemRepositoryPort {

    private static final Sort STABLE_ORDER = Sort.by("id");

    private final ReservationItemR2dbcRepository repository;
    private final R2dbcEntityTemplate template;
    private final ReservationItemPersistenceMapper mapper;

    @Override
    public Mono<ReservationItem> save(ReservationItem reservationItem) {
        return saveEntity(mapper.toEntity(reservationItem)).map(mapper::toDomain);
    }

    @Override
    public Flux<ReservationItem> saveAll(Iterable<ReservationItem> reservationItems) {
        var entities = StreamSupport.stream(reservationItems.spliterator(), false)
                .map(mapper::toEntity)
                .toList();
        return Flux.fromIterable(entities)
                .concatMap(this::saveEntity)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ReservationItem> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Flux<ReservationItem> findByReservationId(UUID reservationId) {
        return repository.findByReservationId(reservationId).map(mapper::toDomain);
    }

    @Override
    public Flux<ReservationItem> findByItemId(UUID itemId) {
        return repository.findByItemId(itemId).map(mapper::toDomain);
    }

    @Override
    public Flux<ReservationItem> findByItemType(ReservationItemType type, int limit) {
        return repository.findByType(type, page(limit)).map(mapper::toDomain);
    }

    private PageRequest page(int limit) {
        return PageRequest.of(0, limit, STABLE_ORDER);
    }

    /**
     * Los ids se generan en el dominio: se consulta la existencia y se hace el
     * upsert explícito (ver {@link ReservationPostgresAdapter#save}).
     */
    private Mono<ReservationItemEntity> saveEntity(ReservationItemEntity entity) {
        return repository.existsById(entity.id())
                .flatMap(exists -> exists ? repository.save(entity) : template.insert(entity));
    }
}
