package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationRequest;
import reactor.core.publisher.Mono;

public interface GetRecommendationsUseCase {

    Mono<Recommendation> handle(RecommendationRequest request);
}
