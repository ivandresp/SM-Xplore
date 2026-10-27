package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReviewListService implements ListReviewsUseCase {

    private final ReviewRepositoryPort reviewRepository;

    @Override
    public Mono<PageResult<Review>> handle(ReviewFilter filter) {
        return reviewRepository.findAll(filter);
    }
}
