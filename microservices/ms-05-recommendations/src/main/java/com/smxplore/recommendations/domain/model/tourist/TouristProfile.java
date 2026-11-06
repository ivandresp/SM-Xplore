package com.smxplore.recommendations.domain.model.tourist;

import com.smxplore.recommendations.domain.exception.InvalidTouristProfileException;

import java.time.Instant;
import java.util.UUID;

public record TouristProfile(
        UUID touristId,
        String language,
        Instant updatedAt
) {

    public TouristProfile(UUID touristId, String language, Instant updatedAt) {
        this.touristId = touristId;
        this.language = language;
        this.updatedAt = updatedAt;
        validate();
    }

    public void validate() {
        validateTouristId();
        validateUpdatedAt();
    }

    private void validateTouristId() {
        if (touristId == null) {
            throw new InvalidTouristProfileException("touristId is required");
        }
    }

    private void validateUpdatedAt() {
        if (updatedAt == null) {
            throw new InvalidTouristProfileException("tourist updatedAt is required");
        }
    }
}
