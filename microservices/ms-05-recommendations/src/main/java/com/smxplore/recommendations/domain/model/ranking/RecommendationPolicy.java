package com.smxplore.recommendations.domain.model.ranking;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationPolicyException;
import lombok.Builder;

@Builder
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

    public void validate() {
        validateLimits();
        validateCategoryDepth();
        validateDecay();
        validateDiversity();
        validateWeights();
    }

    private void validateLimits() {
        if (defaultLimit < 1 || maxLimit < defaultLimit) {
            throw new InvalidRecommendationPolicyException("defaultLimit and maxLimit are inconsistent");
        }
        if (candidateLimit < maxLimit) {
            throw new InvalidRecommendationPolicyException("candidateLimit must be >= maxLimit");
        }
    }

    private void validateCategoryDepth() {
        if (categoryMaxDepth < 0 || categoryMaxDepth > 5) {
            throw new InvalidRecommendationPolicyException("categoryMaxDepth must be between 0 and 5");
        }
    }

    private void validateDecay() {
        if (proximityRadiusKm <= 0.0) {
            throw new InvalidRecommendationPolicyException("proximityRadiusKm must be > 0");
        }
        if (noveltyHalfLifeDays <= 0.0) {
            throw new InvalidRecommendationPolicyException("noveltyHalfLifeDays must be > 0");
        }
        if (preferenceHalfLifeDays <= 0.0) {
            throw new InvalidRecommendationPolicyException("preferenceHalfLifeDays must be > 0");
        }
        if (qualityPriorMean < 1.0 || qualityPriorMean > 5.0) {
            throw new InvalidRecommendationPolicyException("qualityPriorMean must be between 1 and 5");
        }
        if (qualityPriorCount <= 0.0) {
            throw new InvalidRecommendationPolicyException("qualityPriorCount must be > 0");
        }
    }

    private void validateDiversity() {
        if (maxItemsPerSite < 1) {
            throw new InvalidRecommendationPolicyException("maxItemsPerSite must be >= 1");
        }
        if (maxCategoryRatio <= 0.0 || maxCategoryRatio > 1.0) {
            throw new InvalidRecommendationPolicyException("maxCategoryRatio must be in (0, 1]");
        }
    }

    private void validateWeights() {
        if (affinityWeight < 0 || qualityWeight < 0 || proximityWeight < 0 || noveltyWeight < 0) {
            throw new InvalidRecommendationPolicyException("recommendation weights must be >= 0");
        }
        if (affinityWeight + qualityWeight + proximityWeight + noveltyWeight <= 0.0) {
            throw new InvalidRecommendationPolicyException("at least one recommendation weight must be > 0");
        }
    }
}
