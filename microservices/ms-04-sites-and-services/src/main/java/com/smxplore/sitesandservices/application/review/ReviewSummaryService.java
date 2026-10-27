package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReviewSummary;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewSummaryUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ReviewSummaryService implements GetReviewSummaryUseCase {

    private final ReviewRepositoryPort reviewRepository;

    @Override
    public Mono<ReviewSummary> handle(ItemType itemType, UUID itemId) {
        return reviewRepository.summarize(itemType, itemId);
    }
}
