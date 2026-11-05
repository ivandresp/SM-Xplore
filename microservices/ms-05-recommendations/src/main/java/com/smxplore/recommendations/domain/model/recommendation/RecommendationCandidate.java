package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidGeoLocationException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RecommendationCandidate(
        ItemType itemType,
        UUID itemId,
        String name,
        UUID categoryId,
        String categoryName,
        UUID siteId,
        Double latitude,
        Double longitude,
        Instant startsAt,
        Instant endsAt,
        Instant updatedAt,
        double ratingAvg,
        long ratingCount,
        double affinity,
        CandidateOrigin origin
) {

    public void validate() {
        validateItemType();
        validateItemId();
        validateName();
        validateCategoryId();
        validateSiteId();
        validateLocation();
        validateDates();
        validateRating();
        validateAffinity();
        validateOrigin();
    }

    public GeoLocation locationOrNull() {
        if (latitude == null || longitude == null) {
            return null;
        }
        return new GeoLocation(latitude, longitude);
    }

    private void validateItemType() {
        if (itemType == null) {
            throw new InvalidRecommendationException("itemType is required");
        }
    }

    private void validateItemId() {
        if (itemId == null) {
            throw new InvalidRecommendationException("itemId is required");
        }
    }

    private void validateName() {
        if (name == null || name.isBlank()) {
            throw new InvalidRecommendationException("item name is required");
        }
    }

    private void validateCategoryId() {
        if (categoryId == null) {
            throw new InvalidRecommendationException("categoryId is required");
        }
    }

    private void validateSiteId() {
        if (siteId == null) {
            throw new InvalidRecommendationException("siteId is required");
        }
    }

    private void validateLocation() {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new InvalidRecommendationException("latitude and longitude must be provided together");
        }
        try {
            new GeoLocation(latitude, longitude);
        } catch (InvalidGeoLocationException exception) {
            throw new InvalidRecommendationException(exception.getMessage());
        }
    }

    private void validateDates() {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new InvalidRecommendationException("endsAt must be after startsAt");
        }
    }

    private void validateRating() {
        if (!Double.isFinite(ratingAvg) || ratingAvg < 0.0 || ratingAvg > 5.0) {
            throw new InvalidRecommendationException("ratingAvg must be between 0 and 5");
        }
        if (ratingCount < 0) {
            throw new InvalidRecommendationException("ratingCount must be >= 0");
        }
    }

    private void validateAffinity() {
        if (!Double.isFinite(affinity) || affinity < 0.0) {
            throw new InvalidRecommendationException("affinity must be >= 0");
        }
    }

    private void validateOrigin() {
        if (origin == null) {
            throw new InvalidRecommendationException("candidate origin is required");
        }
    }
}
