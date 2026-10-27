package com.smxplore.sitesandservices.domain.model.zone;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Zone {

    private final UUID zoneId;
    private final UUID managerId;
    private final String name;
    private final String type;
    private final String description;
    @Builder.Default
    private final ZoneStatus status = ZoneStatus.ACTIVE;
    private final Instant createdAt;
    private final Instant updatedAt;

    public void validate() {
        validateZoneId(zoneId);
        validateManagerId(managerId);
        validateName(name);
        validateStatus(status);
        validateCreatedAt(createdAt);
        validateUpdatedAt(updatedAt);
        validateChronology(createdAt, updatedAt);
    }

    private static void validateZoneId(UUID zoneId) {
        if (zoneId == null) {
            throw new InvalidCatalogException("zoneId is required");
        }
    }

    private static void validateManagerId(UUID managerId) {
        if (managerId == null) {
            throw new InvalidCatalogException("zone managerId is required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCatalogException("zone name is required");
        }
    }

    private static void validateStatus(ZoneStatus status) {
        if (status == null) {
            throw new InvalidCatalogException("zone status is required");
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidCatalogException("zone createdAt is required");
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new InvalidCatalogException("zone updatedAt is required");
        }
    }

    private static void validateChronology(Instant createdAt, Instant updatedAt) {
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidCatalogException("zone updatedAt cannot be before createdAt");
        }
    }
}
