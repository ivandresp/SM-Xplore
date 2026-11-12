package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;

import java.util.Locale;
import java.util.UUID;

/**
 * Identifies a recommendation result so it can be cached and reused.
 *
 * <p>The key includes the graph version because the projection changes on every applied event:
 * a new version means the cached result no longer describes the current graph.</p>
 *
 * <p>MS05-11 extends the key with the pagination window and the candidate filters. Values are
 * normalized before rendering so equivalent requests share an entry: coordinates are rounded to
 * four decimals (≈11 m), {@code minRating} to two decimals and absent filters use the {@code "-"}
 * sentinel. {@code itemType} is the enum name, not {@code toString()}, so a locale can never change
 * the key.</p>
 */
public record RecommendationCacheKey(
        UUID touristId,
        UUID siteId,
        Double latitude,
        Double longitude,
        int limit,
        int page,
        int size,
        UUID categoryId,
        ItemType itemType,
        Double minRating
) {

    public RecommendationCacheKey {
        if (touristId == null) {
            throw new InvalidRecommendationException("cache key touristId is required");
        }
        if (limit < 1) {
            throw new InvalidRecommendationException("cache key limit must be >= 1");
        }
        if (page < 0) {
            throw new InvalidRecommendationException("cache key page must be >= 0");
        }
        if (size < 1) {
            throw new InvalidRecommendationException("cache key size must be >= 1");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidRecommendationException("cache key location needs both latitude and longitude");
        }
        if (minRating != null && (!Double.isFinite(minRating) || minRating < 0.0 || minRating > 5.0)) {
            throw new InvalidRecommendationException("cache key minRating must be between 0 and 5");
        }
    }

    public String value(long version) {
        return "v" + version + ":" + touristId
                + ":" + (siteId == null ? "-" : siteId)
                + ":" + coordinate(latitude)
                + ":" + coordinate(longitude)
                + ":" + limit
                + ":" + page
                + ":" + size
                + ":" + (categoryId == null ? "-" : categoryId)
                + ":" + (itemType == null ? "-" : itemType.name())
                + ":" + rating(minRating);
    }

    private static String coordinate(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.4f", value);
    }

    private static String rating(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }
}
