package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ReservationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReservationR2dbcRepository extends ReactiveCrudRepository<ReservationEntity, UUID> {

    Flux<ReservationEntity> findAllByTouristId(UUID touristId, Pageable pageable);

    Flux<ReservationEntity> findAllByStatus(ReservationStatus status, Pageable pageable);

    Flux<ReservationEntity> findAllByProviderId(UUID providerId, Pageable pageable);

    Flux<ReservationEntity> findAllByProviderIdAndStatus(UUID providerId, ReservationStatus status, Pageable pageable);

    Flux<ReservationEntity> findAllByGuideId(UUID guideId, Pageable pageable);

    Flux<ReservationEntity> findAllByGuideIdAndStatus(UUID guideId, ReservationStatus status, Pageable pageable);

    @Query("SELECT * FROM reservations WHERE item_id = :itemId AND item_type = :type ORDER BY id LIMIT :limit")
    Flux<ReservationEntity> findAllByItem(UUID itemId, String type, int limit);

    @Query("SELECT * FROM reservations WHERE id = :id FOR UPDATE")
    Mono<ReservationEntity> findByIdForUpdate(UUID id);

    Mono<Long> countByTouristIdAndStatus(UUID touristId, ReservationStatus status);
}
