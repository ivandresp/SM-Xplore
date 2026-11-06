package com.smxplore.activitiesevents.domain.model.event;

import java.util.Set;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    POSTPONED,
    CANCELLED;

    public Set<EventStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT, POSTPONED -> Set.of(PUBLISHED, CANCELLED);
            case PUBLISHED -> Set.of(POSTPONED, CANCELLED);
            case CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(EventStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
