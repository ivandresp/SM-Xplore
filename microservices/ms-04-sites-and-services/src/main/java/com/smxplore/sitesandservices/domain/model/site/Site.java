package com.smxplore.sitesandservices.domain.model.site;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Site {

    private final UUID siteId;
    private final UUID managerId;
    private final String name;
    private final String description;
    private final UUID categoryId;
    private final String categoryName;
    private final UUID zoneId;
    private final String zoneName;
    private final ZoneStatus zoneStatus;
    private final double latitude;
    private final double longitude;
    @Builder.Default
    private final List<String> photosUrls = List.of();
    @Builder.Default
    private final ContentStatus status = ContentStatus.DRAFT;
    @Builder.Default
    private final int maxCapacity = 1;
    @Builder.Default
    private final List<AvailabilitySlot> availability = List.of();
    private final Instant createdAt;
    private final Instant updatedAt;

    public void validate() {
        validateSiteId(siteId);
        validateManagerId(managerId);
        validateName(name);
        validateCategory(categoryId, categoryName);
        validateZone(zoneId, zoneName, zoneStatus);
        validateCoordinates(latitude, longitude);
        validatePhotos(photosUrls);
        validateStatus(status);
        validateCapacity(maxCapacity);
        validateAvailability(availability);
        validateCreatedAt(createdAt);
        validateUpdatedAt(updatedAt);
        validateChronology(createdAt, updatedAt);
    }

    private static void validateSiteId(UUID siteId) {
        if (siteId == null) {
            throw new InvalidCatalogException("siteId is required");
        }
    }

    private static void validateManagerId(UUID managerId) {
        if (managerId == null) {
            throw new InvalidCatalogException("site managerId is required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCatalogException("site name is required");
        }
    }

    private static void validateCategory(UUID categoryId, String categoryName) {
        if (categoryId == null) {
            throw new InvalidCatalogException("site categoryId is required");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new InvalidCatalogException("site categoryName is required");
        }
    }

    private static void validateZone(UUID zoneId, String zoneName, ZoneStatus zoneStatus) {
        if (zoneId == null) {
            throw new InvalidCatalogException("site zoneId is required");
        }
        if (zoneName == null || zoneName.isBlank()) {
            throw new InvalidCatalogException("site zoneName is required");
        }
        if (zoneStatus == null) {
            throw new InvalidCatalogException("site zoneStatus is required");
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidCatalogException("site latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidCatalogException("site longitude must be between -180 and 180");
        }
    }

    private static void validatePhotos(List<String> photosUrls) {
        if (photosUrls == null) {
            throw new InvalidCatalogException("site photosUrls is required");
        }
        if (photosUrls.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("site photosUrls cannot contain null values");
        }
    }

    private static void validateStatus(ContentStatus status) {
        if (status == null) {
            throw new InvalidCatalogException("site status is required");
        }
    }

    private static void validateCapacity(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new InvalidCatalogException("site maxCapacity must be >= 1");
        }
    }

    private static void validateAvailability(List<AvailabilitySlot> availability) {
        if (availability == null) {
            throw new InvalidCatalogException("site availability is required");
        }
        if (availability.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("site availability cannot contain null values");
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidCatalogException("site createdAt is required");
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new InvalidCatalogException("site updatedAt is required");
        }
    }

    private static void validateChronology(Instant createdAt, Instant updatedAt) {
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidCatalogException("site updatedAt cannot be before createdAt");
        }
    }
}
