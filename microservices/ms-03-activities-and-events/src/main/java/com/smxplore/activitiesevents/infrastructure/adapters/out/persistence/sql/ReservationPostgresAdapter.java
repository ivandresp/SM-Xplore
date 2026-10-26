package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ReservationEntity;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper.ReservationPersistenceMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository.ReservationR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReservationPostgresAdapter implements ReservationRepositoryPort {

    private static final Sort STABLE_ORDER = Sort.by("id");

    private final ReservationR2dbcRepository repository;
    private final R2dbcEntityTemplate template;
    private final ReservationPersistenceMapper mapper;

    /**
     * Ids are generated in the domain, so Spring Data's save() cannot decide
     * between insert and update with isNew() (it would always update). Here the
     * existence is checked and the upsert is explicit.
     */
    @Override
    public Mono<Reservation> save(Reservation reservation) {
        var entity = mapper.toEntity(reservation);
        return saveEntity(entity).map(mapper::toDomain);
    }

    @Override
    public Mono<Reservation> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Reservation> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Flux<Reservation> findAllByTouristId(UUID touristId, int limit) {
        return repository.findAllByTouristId(touristId, page(limit)).map(mapper::toDomain);
    }

    @Override
    public Flux<Reservation> findAllByStatus(ReservationStatus status, int limit) {
        return repository.findAllByStatus(status, page(limit)).map(mapper::toDomain);
    }

    @Override
    public Flux<Reservation> findAllByProviderId(UUID providerId, ReservationStatus status, int limit) {
        return (status == null
                ? repository.findAllByProviderId(providerId, page(limit))
                : repository.findAllByProviderIdAndStatus(providerId, status, page(limit)))
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Reservation> findAllByGuideId(UUID guideId, ReservationStatus status, int limit) {
        return (status == null
                ? repository.findAllByGuideId(guideId, page(limit))
                : repository.findAllByGuideIdAndStatus(guideId, status, page(limit)))
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Reservation> findAllByItem(UUID itemId, ReservationItemType type, int limit) {
        return repository.findAllByItem(itemId, type.name(), limit).map(mapper::toDomain);
    }

    @Override
    public Mono<Integer> sumActivePeopleForItem(UUID itemId, ReservationItemType type) {
        var sql = """
                SELECT COALESCE(SUM(number_of_people), 0) AS reserved
                FROM reservations
                WHERE item_id = :itemId
                  AND item_type = :type
                  AND status = 'CONFIRMED'
                """;
        return template.getDatabaseClient().sql(sql)
                .bind("itemId", itemId)
                .bind("type", type.name())
                .map((row, metadata) -> row.get("reserved", Long.class))
                .one()
                .map(Long::intValue)
                .defaultIfEmpty(0);
    }

    @Override
    public Mono<Long> countActiveByTourist(UUID touristId) {
        return repository.countActiveByTourist(touristId);
    }

    @Override
    public Mono<Void> lockTourist(UUID touristId) {
        return template.getDatabaseClient()
                .sql("SELECT pg_advisory_xact_lock(hashtext(:touristId))")
                .bind("touristId", touristId.toString())
                .then();
    }

    @Override
    public Mono<Integer> rescheduleItem(UUID itemId, ReservationItemType type, Instant newStartsAt) {
        return template.getDatabaseClient()
                .sql("""
                        UPDATE reservations
                        SET item_starts_at = :startsAt,
                            booked_for     = :startsAt
                        WHERE item_id = :itemId
                          AND item_type = :type
                          AND status = 'CONFIRMED'
                        """)
                .bind("startsAt", newStartsAt)
                .bind("itemId", itemId)
                .bind("type", type.name())
                .fetch().rowsUpdated()
                .map(Long::intValue);
    }

    private PageRequest page(int limit) {
        return PageRequest.of(0, limit, STABLE_ORDER);
    }

    private Mono<ReservationEntity> saveEntity(ReservationEntity entity) {
        return repository.existsById(entity.id())
                .flatMap(exists -> exists ? repository.save(entity) : insertWithCode(entity));
    }

    /** The readable code (R-####) is generated once, when the reservation is first inserted. */
    private Mono<ReservationEntity> insertWithCode(ReservationEntity entity) {
        if (entity.code() != null) {
            return template.insert(entity);
        }
        return template.getDatabaseClient()
                .sql("SELECT nextval('reservation_code_seq')")
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .flatMap(sequence -> template.insert(withCode(entity, "R-" + sequence)));
    }

    private ReservationEntity withCode(ReservationEntity entity, String code) {
        return new ReservationEntity(entity.id(), entity.touristId(), entity.itemType(), entity.itemId(),
                entity.itemStartsAt(), entity.unitPrice(), entity.numberOfPeople(), entity.total(),
                entity.paymentMethod(), entity.status(), entity.bookedAt(), entity.bookedFor(), entity.notes(),
                entity.providerId(), entity.guideId(), code);
    }
}
