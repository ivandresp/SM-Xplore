package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;

import java.util.Locale;
import java.util.UUID;

/**
 * Identifies a recommendation result so it can be cached and reused.
 *
 * <p>The key includes the graph version because the projection changes on every applied event:
 * a new version means the cached result no longer describes the current graph.
 */
public record RecommendationCacheKey(
        UUID touristId,
        UUID siteId,
        Double latitude,
        Double longitude,
        int limit
) {

    public RecommendationCacheKey {
        if (touristId == null) {
            throw new InvalidRecommendationException("cache key touristId is required");
        }
        if (limit < 1) {
            throw new InvalidRecommendationException("cache key limit must be >= 1");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidRecommendationException("cache key location needs both latitude and longitude");
        }
    }

    public String value(long version) {
        return "v" + version + ":" + touristId
                + ":" + (siteId == null ? "-" : siteId)
                + ":" + coordinate(latitude)
                + ":" + coordinate(longitude)
                + ":" + limit;
    }

    private static String coordinate(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.4f", value);
    }
}
