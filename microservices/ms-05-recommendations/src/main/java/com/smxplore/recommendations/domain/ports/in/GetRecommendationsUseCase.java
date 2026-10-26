package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.Recommendation;
import com.smxplore.recommendations.domain.model.RecommendationQuery;
import reactor.core.publisher.Mono;

public interface GetRecommendationsUseCase {

    Mono<Recommendation> handle(RecommendationQuery query);
}
