package com.smxplore.sitesandservices.domain.exception;

import java.util.UUID;

public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException(UUID touristId, String itemType, UUID itemId) {
        super("Tourist " + touristId + " already reviewed " + itemType + "/" + itemId);
    }
}
