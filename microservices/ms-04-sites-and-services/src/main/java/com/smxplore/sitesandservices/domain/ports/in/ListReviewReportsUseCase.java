package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportFilter;
import reactor.core.publisher.Mono;

public interface ListReviewReportsUseCase {

    Mono<PageResult<ReviewReport>> handle(ReviewReportFilter filter, Actor actor);
}
