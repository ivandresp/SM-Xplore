package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import reactor.core.publisher.Mono;

public interface ListReviewsUseCase {

    Mono<PageResult<Review>> handle(ReviewFilter filter);
}
