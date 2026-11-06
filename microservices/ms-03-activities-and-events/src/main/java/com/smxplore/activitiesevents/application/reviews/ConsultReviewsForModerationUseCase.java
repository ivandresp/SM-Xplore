package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsForModerationUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** Global administrative queue; the caller decides which status to inspect. */
@RequiredArgsConstructor
public class ConsultReviewsForModerationUseCase implements ConsultReviewsForModerationUseCasePort {

    private final ReviewRepositoryPort reviewRepo;

    @Override
    public Flux<Review> handle(ReviewStatus status, int limit) {
        if (status == null) {
            return Flux.empty();
        }
        return reviewRepo.findByStatus(status, limit);
    }
}
