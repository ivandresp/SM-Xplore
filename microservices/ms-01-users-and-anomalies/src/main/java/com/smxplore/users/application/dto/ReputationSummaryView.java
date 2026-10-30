package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ReputationSummary;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.Map;
import java.util.UUID;

public record ReputationSummaryView(
        String targetType, UUID targetId, long reviewCount, double averageRating, Map<Integer, Long> distribution) {

    public static ReputationSummaryView from(ReviewTargetType targetType, UUID targetId, ReputationSummary summary) {
        return new ReputationSummaryView(
                targetType.code(), targetId, summary.reviewCount(), summary.averageRating(), summary.distribution());
    }
}
