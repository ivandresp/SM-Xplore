package com.smxplore.recommendations.domain.model.catalog;

/**
 * Operational status of a zone as published by ms-04 in {@code catalog.zone.changed}.
 */
public enum ZoneStatus {
    ACTIVE,
    CONGESTED,
    INACTIVE
}
