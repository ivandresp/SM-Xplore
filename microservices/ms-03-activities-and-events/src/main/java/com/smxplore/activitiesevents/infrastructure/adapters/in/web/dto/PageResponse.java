package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.common.PageResult;

import java.util.List;
import java.util.function.Function;

/** Stable pagination envelope for the Web API. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages
) {

    /** Wraps a page already limited by the database. */
    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.total(),
                result.totalPages());
    }

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / size);
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);
    }
}
