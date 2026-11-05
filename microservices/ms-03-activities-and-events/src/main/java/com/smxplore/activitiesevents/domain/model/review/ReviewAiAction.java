package com.smxplore.activitiesevents.domain.model.review;

/**
 * What happens to a review the automatic filter flags. Mirrors the ms-04 policy so the same
 * environment variables can configure both services.
 */
public enum ReviewAiAction {
    PENDING_REVIEW,
    HIDE
}
