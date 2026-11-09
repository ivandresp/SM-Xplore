package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;

/**
 * Observability of the recommendation use case: how many answers are served, how often the cache
 * resolves them and how diverse the selection is.
 *
 * <p>Implementations must never throw: metrics cannot break a request.
 */
public interface RecommendationMetricsPort {

    void served(RecommendationSource source, int items);

    void cacheHit();

    void cacheMiss();

    void diversity(int distinctCategories, int distinctSites);

    /**
     * An administrator read the recommendations of a different tourist (MS05-13). Counted so the
     * support capability is observable and can be alerted on.
     */
    void adminCrossTouristAccess();
}
