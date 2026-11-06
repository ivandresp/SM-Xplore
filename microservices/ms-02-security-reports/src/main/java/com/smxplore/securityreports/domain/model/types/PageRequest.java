package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidPageSizeException;

/**
 * Requested page size and the cursor of the previous page. A {@code null}
 * cursor means that the first page is being requested.
 */
public record PageRequest(int size, Cursor cursor) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidPageSizeException(
                    "Page size must be between 1 and %d.".formatted(MAX_SIZE));
        }
    }

    public static PageRequest first(int size) {
        return new PageRequest(size, null);
    }

    public boolean isFirstPage() {
        return cursor == null;
    }
}
