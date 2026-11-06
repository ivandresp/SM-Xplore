package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Review {

    private final UUID reviewId;
    private final UUID touristId;
    private final String touristName;
    private final ItemType itemType;
    private final UUID itemId;
    private final String itemName;
    private final int rating;
    private final String commentary;
    @Builder.Default
    private final List<String> photosUrls = List.of();
    private final String response;
    private final Instant respondedAt;
    @Builder.Default
    private final int helpfulReactions = 0;
    @Builder.Default
    private final ReviewStatus status = ReviewStatus.PUBLISHED;
    private final ReviewAiAssessment aiAssessment;
    @Builder.Default
    private final boolean aiRetained = false;
    private final Instant createdAt;
    private final Instant updatedAt;

    public void validate() {
        validateReviewId(reviewId);
        validateTourist(touristId, touristName);
        validateItem(itemType, itemId, itemName);
        validateRating(rating);
        validatePhotos(photosUrls);
        validateResponse(response, respondedAt);
        validateHelpfulReactions(helpfulReactions);
        validateStatus(status);
        validateAiAssessment(aiAssessment, aiRetained);
        validateCreatedAt(createdAt);
        validateUpdatedAt(updatedAt);
        validateChronology(createdAt, updatedAt);
    }

    /**
     * Guarda el resultado del filtro y, cuando la reseña se marca con confianza
     * suficiente, la deja pendiente de revisión o la oculta según la política.
     * La reseña es inmutable, así que devuelve una nueva instancia.
     */
    public Review withAiAssessment(ReviewAiAssessment assessment, ReviewAiPolicy policy, Instant now) {
        if (assessment == null) {
            throw new InvalidCatalogException("review AI assessment is required");
        }
        if (policy == null) {
            throw new InvalidCatalogException("review AI policy is required");
        }
        boolean retained = policy.shouldFlag(assessment);
        Review.ReviewBuilder builder = toBuilder()
                .aiAssessment(assessment)
                .aiRetained(retained);
        if (retained) {
            builder.status(policy.flaggedStatus())
                    .updatedAt(now);
        }
        Review updated = builder.build();
        updated.validate();
        return updated;
    }

    private static void validateReviewId(UUID reviewId) {
        if (reviewId == null) {
            throw new InvalidCatalogException("reviewId is required");
        }
    }

    private static void validateTourist(UUID touristId, String touristName) {
        if (touristId == null) {
            throw new InvalidCatalogException("review touristId is required");
        }
        if (touristName == null || touristName.isBlank()) {
            throw new InvalidCatalogException("review touristName is required");
        }
    }

    private static void validateItem(ItemType itemType, UUID itemId, String itemName) {
        if (itemType == null) {
            throw new InvalidCatalogException("review itemType is required");
        }
        if (itemId == null) {
            throw new InvalidCatalogException("review itemId is required");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new InvalidCatalogException("review itemName is required");
        }
    }

    private static void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new InvalidCatalogException("review rating must be between 1 and 5");
        }
    }

    private static void validatePhotos(List<String> photosUrls) {
        if (photosUrls == null) {
            throw new InvalidCatalogException("review photosUrls is required");
        }
        if (photosUrls.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("review photosUrls cannot contain null values");
        }
    }

    private static void validateResponse(String response, Instant respondedAt) {
        if (response != null && !response.isBlank() && respondedAt == null) {
            throw new InvalidCatalogException("review respondedAt is required when response is present");
        }
    }

    private static void validateHelpfulReactions(int helpfulReactions) {
        if (helpfulReactions < 0) {
            throw new InvalidCatalogException("review helpfulReactions must be >= 0");
        }
    }

    private static void validateStatus(ReviewStatus status) {
        if (status == null) {
            throw new InvalidCatalogException("review status is required");
        }
    }

    private static void validateAiAssessment(ReviewAiAssessment assessment, boolean retained) {
        if (retained && assessment == null) {
            throw new InvalidCatalogException("review aiRetained requires an AI assessment");
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidCatalogException("review createdAt is required");
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new InvalidCatalogException("review updatedAt is required");
        }
    }

    private static void validateChronology(Instant createdAt, Instant updatedAt) {
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidCatalogException("review updatedAt cannot be before createdAt");
        }
    }
}
