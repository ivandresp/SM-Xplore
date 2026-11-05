package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.Review;
import reactor.core.publisher.Mono;

public interface ListAiFlaggedReviewsUseCase {

    Mono<PageResult<Review>> handle(int page, int size);
}
