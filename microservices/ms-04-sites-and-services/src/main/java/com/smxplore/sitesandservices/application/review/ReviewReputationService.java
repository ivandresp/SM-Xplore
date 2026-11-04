package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReputationMetrics;
import com.smxplore.sitesandservices.domain.ports.in.GetReputationUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ReviewReputationService implements GetReputationUseCase {

    private final ReviewRepositoryPort reviewRepository;

    @Override
    public Mono<ReputationMetrics> handle(ItemType itemType, UUID itemId) {
        return reviewRepository.reputation(itemType, itemId);
    }
}
