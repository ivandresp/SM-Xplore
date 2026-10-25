package com.smxplore.activitiesevents.domain.model.review;

import java.util.UUID;

public record ReviewMetrics(UUID itemId, ReviewItemType itemType, long count, double averageRating) {
}
