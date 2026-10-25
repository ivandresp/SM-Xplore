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

    @Override
    public Mono<Integer> sumActivePeopleForItem(UUID reservationId, UUID itemId, ReservationItemType type) {
        var sql = """
                SELECT COALESCE(SUM(r.number_of_people), 0) AS reserved
                FROM reservations r
                JOIN reservation_items i ON i.reservation_id = r.id
                WHERE i.item_id = :itemId
                  AND i.type = :type
                  AND r.id <> :reservationId
                  AND r.status IN ('PENDING', 'CONFIRMED', 'CANCELLATION_REQUESTED')
                """;
        return template.getDatabaseClient().sql(sql)
                .bind("reservationId", reservationId)
                .bind("itemId", itemId)
                .bind("type", type.name())
                .map((row, metadata) -> row.get("reserved", Long.class))
                .one()
                .map(Long::intValue)
                .defaultIfEmpty(0);
    }

    private PageRequest page(int limit) {
        return PageRequest.of(0, limit, STABLE_ORDER);
    }

    private Mono<ReservationItemEntity> saveEntity(ReservationItemEntity entity) {
        // Ids are generated in the domain and items are insert-only, so existence cannot be true.
        return template.insert(entity);
    }
}
