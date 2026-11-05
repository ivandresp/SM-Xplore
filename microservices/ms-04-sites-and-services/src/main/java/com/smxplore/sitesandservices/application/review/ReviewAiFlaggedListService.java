package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.ports.in.ListAiFlaggedReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReviewAiFlaggedListService implements ListAiFlaggedReviewsUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepositoryPort reviewRepository;

    @Override
    public Mono<PageResult<Review>> handle(int page, int size) {
        if (page < 0) {
            return Mono.error(new InvalidCatalogException("page must be >= 0"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            return Mono.error(new InvalidCatalogException(
                    "size must be between 1 and " + MAX_PAGE_SIZE));
        }
        return reviewRepository.findAiFlagged(page, size);
    }
}
