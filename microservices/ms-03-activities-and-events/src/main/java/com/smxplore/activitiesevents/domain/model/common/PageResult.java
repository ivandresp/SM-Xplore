package com.smxplore.activitiesevents.domain.model.common;

import java.util.List;

/**
 * One page of results. The database applies the offset and limit, so {@code total} is the only
 * extra query needed and the caller never has to load a whole table to slice it in memory.
 */
public record PageResult<T>(List<T> content, int page, int size, long total) {

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), page, size, 0);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
