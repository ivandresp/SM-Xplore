package com.smxplore.users.infrastructure.adapter.out.persistence.mongo;

import java.util.UUID;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

interface ReviewMongoRepository extends ReactiveMongoRepository<ReviewDocument, String> {

    Mono<Boolean> existsByTouristIdAndTargetTypeAndTargetId(UUID touristId, String targetType, UUID targetId);
}
