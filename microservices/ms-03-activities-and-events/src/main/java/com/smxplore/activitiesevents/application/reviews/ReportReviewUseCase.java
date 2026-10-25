package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ReportReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ReportReviewUseCase implements ReportReviewUseCasePort {

    private final ReviewRepositoryPort reviewRepo;

    @Override
    public Mono<Review> handle(UUID reviewId, String reason) {
        return reviewRepo.findById(reviewId)
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reviewId)))
                .map(review -> review.withReport(reason))
                .flatMap(reviewRepo::save);
    }
}
