package com.smxplore.recommendations.domain.model.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record Preference(
        UUID touristId,
        UUID categoryId,
        double weight,
        PreferenceSource source,
        Instant updatedAt
) {

    public void validate() {
        validateTouristId();
        validateCategoryId();
        validateWeight();
        validateSource();
        validateUpdatedAt();
    }

    private void validateTouristId() {
        if (touristId == null) {
            throw new InvalidPreferenceException("touristId is required");
        }
    }

    private void validateCategoryId() {
        if (categoryId == null) {
            throw new InvalidPreferenceException("categoryId is required");
        }
    }

    private void validateWeight() {
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new InvalidPreferenceException("preference weight must be > 0");
        }
    }

    private void validateSource() {
        if (source == null) {
            throw new InvalidPreferenceException("preference source is required");
        }
    }

    private void validateUpdatedAt() {
        if (updatedAt == null) {
            throw new InvalidPreferenceException("preference updatedAt is required");
        }
    }
}
