package com.smxplore.sitesandservices.domain.model.common;

public enum ContentStatus {
    DRAFT,
    PUBLISHED,
    INACTIVE;

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
