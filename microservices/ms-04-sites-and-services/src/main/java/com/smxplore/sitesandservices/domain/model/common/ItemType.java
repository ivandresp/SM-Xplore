package com.smxplore.sitesandservices.domain.model.common;

public enum ItemType {
    SITE,
    SERVICE,
    ACTIVITY,
    EVENT;

    public boolean isSiteOrService() {
        return this == SITE || this == SERVICE;
    }
}
