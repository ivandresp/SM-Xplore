package com.smxplore.activitiesevents.domain.model.review;

/** Text and metadata sent to the automatic review filter. */
public record ReviewAiRequest(String targetType, int rating, String comment) {
}
