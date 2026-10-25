package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ActivityReviewDocument;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ActivityReviewMongoRepository extends ReactiveMongoRepository<ActivityReviewDocument, UUID> {

    Flux<ActivityReviewDocument> findByItemIdOrderByCreatedAtDesc(UUID itemId);

    @Query(value = "{ 'itemId': ?0, 'user.id': ?1 }", exists = true)
    Mono<Boolean> existsByItemAndUser(UUID itemId, UUID userId);
}
