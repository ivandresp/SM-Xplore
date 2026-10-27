package com.smxplore.sitesandservices.domain.model.service;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Service {

    private final UUID serviceId;
    private final UUID providerId;
    private final String providerName;
    private final UUID siteId;
    private final String siteName;
    private final UUID zoneId;
    private final String zoneName;
    private final ZoneStatus zoneStatus;
    private final UUID categoryId;
    private final String categoryName;
    private final String name;
    private final String description;
    private final String type;
    private final double latitude;
    private final double longitude;
    @Builder.Default
    private final BigDecimal price = BigDecimal.ZERO;
    @Builder.Default
    private final ContentStatus status = ContentStatus.DRAFT;
    @Builder.Default
    private final int maxCapacity = 1;
    @Builder.Default
    private final List<AvailabilitySlot> availability = List.of();
    @Builder.Default
    private final List<String> photosUrls = List.of();
    private final String cancellationPolicy;
    private final Instant createdAt;
    private final Instant updatedAt;

    public void validate() {
        validateServiceId(serviceId);
        validateProvider(providerId, providerName);
        validateSite(siteId, siteName);
        validateZone(zoneId, zoneName, zoneStatus);
        validateCategory(categoryId, categoryName);
        validateName(name);
        validateType(type);
        validateCoordinates(latitude, longitude);
        validatePrice(price);
        validateStatus(status);
        validateCapacity(maxCapacity);
        validateAvailability(availability);
        validatePhotos(photosUrls);
        validateCreatedAt(createdAt);
        validateUpdatedAt(updatedAt);
        validateChronology(createdAt, updatedAt);
    }

    private static void validateServiceId(UUID serviceId) {
        if (serviceId == null) {
            throw new InvalidCatalogException("serviceId is required");
        }
    }

    private static void validateProvider(UUID providerId, String providerName) {
        if (providerId == null) {
            throw new InvalidCatalogException("service providerId is required");
        }
        if (providerName == null || providerName.isBlank()) {
            throw new InvalidCatalogException("service providerName is required");
        }
    }

    private static void validateSite(UUID siteId, String siteName) {
        if (siteId == null) {
            throw new InvalidCatalogException("service siteId is required");
        }
        if (siteName == null || siteName.isBlank()) {
            throw new InvalidCatalogException("service siteName is required");
        }
    }

    private static void validateZone(UUID zoneId, String zoneName, ZoneStatus zoneStatus) {
        if (zoneId == null) {
            throw new InvalidCatalogException("service zoneId is required");
        }
        if (zoneName == null || zoneName.isBlank()) {
            throw new InvalidCatalogException("service zoneName is required");
        }
        if (zoneStatus == null) {
            throw new InvalidCatalogException("service zoneStatus is required");
        }
    }

    private static void validateCategory(UUID categoryId, String categoryName) {
        if (categoryId == null) {
            throw new InvalidCatalogException("service categoryId is required");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new InvalidCatalogException("service categoryName is required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCatalogException("service name is required");
        }
    }

    private static void validateType(String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidCatalogException("service type is required");
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidCatalogException("service latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidCatalogException("service longitude must be between -180 and 180");
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new InvalidCatalogException("service price is required");
        }
        if (price.signum() < 0) {
            throw new InvalidCatalogException("service price must be >= 0");
        }
    }

    private static void validateStatus(ContentStatus status) {
        if (status == null) {
            throw new InvalidCatalogException("service status is required");
        }
    }

    private static void validateCapacity(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new InvalidCatalogException("service maxCapacity must be >= 1");
        }
    }

    private static void validateAvailability(List<AvailabilitySlot> availability) {
        if (availability == null) {
            throw new InvalidCatalogException("service availability is required");
        }
        if (availability.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("service availability cannot contain null values");
        }
    }

    private static void validatePhotos(List<String> photosUrls) {
        if (photosUrls == null) {
            throw new InvalidCatalogException("service photosUrls is required");
        }
        if (photosUrls.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogException("service photosUrls cannot contain null values");
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidCatalogException("service createdAt is required");
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new InvalidCatalogException("service updatedAt is required");
        }
    }

    private static void validateChronology(Instant createdAt, Instant updatedAt) {
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidCatalogException("service updatedAt cannot be before createdAt");
        }
    }
}
