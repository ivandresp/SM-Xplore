package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.EventEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface EventR2dbcRepository extends ReactiveCrudRepository<EventEntity, UUID> {

    Flux<EventEntity> findByNameContainingIgnoreCase(String name);
}
