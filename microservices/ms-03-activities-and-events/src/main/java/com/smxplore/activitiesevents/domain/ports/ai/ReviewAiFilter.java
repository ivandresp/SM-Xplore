package com.smxplore.activitiesevents.domain.ports.ai;

import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiRequest;
import reactor.core.publisher.Mono;

/** Automatic moderation filter. Implementations must not throw: outages are reported as UNAVAILABLE. */
public interface ReviewAiFilter {

    Mono<ReviewAiAssessment> assess(ReviewAiRequest request);
}
