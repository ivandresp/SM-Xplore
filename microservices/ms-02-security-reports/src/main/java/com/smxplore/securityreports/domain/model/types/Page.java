package com.smxplore.securityreports.domain.model.types;

import java.util.List;

/**
 * A slice of a larger result set. When {@code nextCursor} is {@code null}
 * there are no more elements to fetch.
 */
public record Page<T>(List<T> content, Cursor nextCursor) {
    public Page {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public boolean hasNext() {
        return nextCursor != null;
    }

    public static <T> Page<T> empty() {
        return new Page<>(List.of(), null);
    }
}
