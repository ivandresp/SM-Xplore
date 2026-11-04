package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import java.util.List;

/**
 * Cursor-based page returned to clients. The {@code nextCursor} is opaque:
 * clients must send it back as the {@code cursor} query parameter to fetch
 * the next page, and stop when it is {@code null}.
 */
public record PageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext
) {
    public static <T> PageResponse<T> of(List<T> content, String nextCursor) {
        return new PageResponse<>(content, nextCursor, nextCursor != null);
    }
}
