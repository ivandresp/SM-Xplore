package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidRecommendationException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Diversified recommendation list plus the pagination window that was served.
 *
 * <p>The full diversified list is generated once and then sliced into pages, so the pagination
 * metadata describes the generated list and not a second graph query. The cached entry stores the
 * page, which is why {@link RecommendationCacheKey} includes {@code page}/{@code size}.</p>
 */
public record Recommendation(
        UUID touristId,
        Instant generatedAt,
        RecommendationSource source,
        List<RecommendationItem> items,
        int page,
        int size,
        int total,
        int totalPages,
        boolean hasNext
) {

    public Recommendation(
            UUID touristId,
            Instant generatedAt,
            RecommendationSource source,
            List<RecommendationItem> items,
            int page,
            int size,
            int total,
            int totalPages,
            boolean hasNext
    ) {
        this.touristId = touristId;
        this.generatedAt = generatedAt;
        this.source = source;
        this.items = copyItems(items);
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        validate();
    }

    public void validate() {
        validateTouristId();
        validateGeneratedAt();
        validateSource();
        validateItems();
        validatePagination();
    }

    private static List<RecommendationItem> copyItems(List<RecommendationItem> items) {
        if (items == null) {
            throw new InvalidRecommendationException("items are required");
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRecommendationException("items cannot contain null values");
        }
        return List.copyOf(items);
    }

    private void validateTouristId() {
        if (touristId == null) {
            throw new InvalidRecommendationException("touristId is required");
        }
    }

    private void validateGeneratedAt() {
        if (generatedAt == null) {
            throw new InvalidRecommendationException("generatedAt is required");
        }
    }

    private void validateSource() {
        if (source == null) {
            throw new InvalidRecommendationException("recommendation source is required");
        }
    }

    private void validateItems() {
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRecommendationException("items cannot contain null values");
        }
    }

    private void validatePagination() {
        if (page < 0) {
            throw new InvalidRecommendationException("page must be >= 0");
        }
        if (size < 1) {
            throw new InvalidRecommendationException("size must be >= 1");
        }
        if (total < 0) {
            throw new InvalidRecommendationException("total must be >= 0");
        }
        int expectedTotalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        if (totalPages != expectedTotalPages) {
            throw new InvalidRecommendationException(
                    "totalPages must be " + expectedTotalPages + " for total=" + total + " and size=" + size);
        }
        boolean expectedHasNext = ((long) page + 1) * size < total;
        if (hasNext != expectedHasNext) {
            throw new InvalidRecommendationException("hasNext is inconsistent with page, size and total");
        }
    }
}
