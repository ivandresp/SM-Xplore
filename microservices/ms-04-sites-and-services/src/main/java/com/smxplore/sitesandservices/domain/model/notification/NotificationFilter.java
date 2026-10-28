package com.smxplore.sitesandservices.domain.model.notification;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.util.UUID;

public record NotificationFilter(
        UUID recipientId,
        boolean unreadOnly,
        int page,
        int size
) {

    public NotificationFilter {
        if (recipientId == null) {
            throw new InvalidCatalogException("notification recipientId is required");
        }
        if (page < 0) {
            throw new InvalidCatalogException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new InvalidCatalogException("size must be between 1 and 100");
        }
    }
}
