package com.smxplore.recommendations.domain.ports.out;

import java.util.UUID;

/**
 * Per-user request budget for the recommendation query path (MS05-02). A cache miss costs
 * two Cypher queries, so the budget limits how many misses one authenticated tourist can
 * trigger per window before the API answers 429.
 *
 * <p>The check is synchronous: it only touches an in-memory counter and must not add a
 * network hop to the request path.</p>
 */
public interface RecommendationQuotaPort {

    /**
     * Consumes one unit of the caller's budget.
     *
     * @param touristId authenticated tourist (JWT subject)
     * @throws com.smxplore.recommendations.domain.exception.RateLimitExceededException
     *         when the budget for the current window is exhausted
     */
    void check(UUID touristId);
}
