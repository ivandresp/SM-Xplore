package com.smxplore.recommendations.domain.model;

public record RecommendationPolicy(
        int defaultLimit,
        int maxLimit,
        int candidateLimit,
        int categoryMaxDepth,
        double proximityRadiusKm,
        double noveltyHalfLifeDays,
        double preferenceHalfLifeDays,
        double qualityPriorMean,
        double qualityPriorCount,
        int maxItemsPerSite,
        double maxCategoryRatio,
        double affinityWeight,
        double qualityWeight,
        double proximityWeight,
        double noveltyWeight
) {

    public RecommendationPolicy {
        if (defaultLimit < 1 || maxLimit < defaultLimit) {
            throw new IllegalArgumentException("Invalid recommendation limits");
        }
        if (candidateLimit < maxLimit) {
            throw new IllegalArgumentException("candidateLimit must be >= maxLimit");
        }
        if (categoryMaxDepth < 0 || categoryMaxDepth > 5) {
            throw new IllegalArgumentException("categoryMaxDepth must be between 0 and 5");
        }
        if (proximityRadiusKm <= 0.0) {
            throw new IllegalArgumentException("proximityRadiusKm must be > 0");
        }
        if (noveltyHalfLifeDays <= 0.0) {
            throw new IllegalArgumentException("noveltyHalfLifeDays must be > 0");
        }
        if (preferenceHalfLifeDays <= 0.0) {
            throw new IllegalArgumentException("preferenceHalfLifeDays must be > 0");
        }
        if (qualityPriorCount <= 0.0) {
            throw new IllegalArgumentException("qualityPriorCount must be > 0");
        }
        if (maxItemsPerSite < 1) {
            throw new IllegalArgumentException("maxItemsPerSite must be >= 1");
        }
        if (maxCategoryRatio <= 0.0 || maxCategoryRatio > 1.0) {
            throw new IllegalArgumentException("maxCategoryRatio must be in (0, 1]");
        }
        if (affinityWeight < 0 || qualityWeight < 0 || proximityWeight < 0 || noveltyWeight < 0) {
            throw new IllegalArgumentException("Recommendation weights must be >= 0");
        }
        if (affinityWeight + qualityWeight + proximityWeight + noveltyWeight <= 0.0) {
            throw new IllegalArgumentException("At least one recommendation weight must be > 0");
        }
    }
}
