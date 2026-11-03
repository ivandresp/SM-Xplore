package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReputationMetrics;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetReputationUseCase {

    Mono<ReputationMetrics> handle(ItemType itemType, UUID itemId);
}
