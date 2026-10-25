package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewMetricsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReviewMetricsUseCase implements ConsultReviewMetricsUseCasePort {

    private final ReviewRepositoryPort reviewRepo;

    @Override
    public Mono<ReviewMetrics> handle(UUID itemId, ReviewItemType itemType) {
        return reviewRepo.findByItem(itemId, itemType)
                .collectList()
                .map(reviews -> {
                    var count = reviews.size();
                    var average = reviews.stream().mapToInt(review -> review.getRating()).average().orElse(0.0);
                    return new ReviewMetrics(itemId, itemType, count, average);
                });
    }
}
