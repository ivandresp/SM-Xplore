package com.smxplore.recommendations.domain.ports.out;

import java.util.UUID;

/**
 * Per-user request budgets on the paths that write to the graph or cost several Cypher
 * queries. The recommendation budget is MS05-02; the write budgets for views and preferences
 * are MS05-04.
 *
 * <p>The check is synchronous: it only touches an in-memory counter and must not add a
 * network hop to the request path. A 429 with {@code Retry-After} is the contract.</p>
 */
public interface RecommendationQuotaPort {

    /**
     * Consumes one unit of the caller's recommendation budget.
     *
     * @param touristId authenticated tourist (JWT subject)
     * @throws com.smxplore.recommendations.domain.exception.RateLimitExceededException
     *         when the budget for the current window is exhausted
     */
    void check(UUID touristId);

    /**
     * Consumes one unit of the caller's interaction (view) budget (MS05-04).
     */
    void checkView(UUID touristId);

    /**
     * Consumes one unit of the caller's preference write budget (MS05-04).
     */
    void checkPreference(UUID touristId);
}
