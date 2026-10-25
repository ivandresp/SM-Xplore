package com.smxplore.activitiesevents.domain.model.activity;

import java.util.Set;

public enum ActivityStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    CANCELLED;

    public Set<ActivityStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT, PAUSED -> Set.of(PUBLISHED, CANCELLED);
            case PUBLISHED -> Set.of(PAUSED, CANCELLED);
            case CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(ActivityStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
