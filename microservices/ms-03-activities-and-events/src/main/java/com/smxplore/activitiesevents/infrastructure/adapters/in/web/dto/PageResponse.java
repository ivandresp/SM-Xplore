package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import java.util.List;

/** Stable pagination envelope for the Web API. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / size);
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);
    }
}
