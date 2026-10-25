package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.EventEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EventR2dbcRepository extends ReactiveCrudRepository<EventEntity, UUID> {

    Flux<EventEntity> findByNameContainingIgnoreCase(String name);

    @Query("SELECT * FROM evento WHERE id = :id FOR UPDATE")
    Mono<EventEntity> findByIdForUpdate(UUID id);
}
