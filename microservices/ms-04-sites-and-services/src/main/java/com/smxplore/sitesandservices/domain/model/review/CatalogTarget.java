package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;

import java.util.UUID;

public record CatalogTarget(
        ItemType itemType,
        UUID itemId,
        String name,
        UUID ownerId,
        ContentStatus status
) {

    public CatalogTarget {
        validateItemType(itemType);
        validateItemId(itemId);
        validateName(name);
        validateOwnerId(ownerId);
        validateStatus(status);
    }

    private static void validateItemType(ItemType itemType) {
        if (itemType == null) {
            throw new InvalidCatalogException("catalog target itemType is required");
        }
    }

    private static void validateItemId(UUID itemId) {
        if (itemId == null) {
            throw new InvalidCatalogException("catalog target itemId is required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCatalogException("catalog target name is required");
        }
    }

    private static void validateOwnerId(UUID ownerId) {
        if (ownerId == null) {
            throw new InvalidCatalogException("catalog target ownerId is required");
        }
    }

    private static void validateStatus(ContentStatus status) {
        if (status == null) {
            throw new InvalidCatalogException("catalog target status is required");
        }
    }
}
