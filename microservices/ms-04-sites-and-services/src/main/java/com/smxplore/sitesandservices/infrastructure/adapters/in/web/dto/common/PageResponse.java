package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common;

import com.smxplore.sitesandservices.domain.model.common.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages
) {

    public static <S, T> PageResponse<T> from(PageResult<S> page, Function<S, T> mapper) {
        int totalPages = page.size() == 0 ? 0 : (int) Math.ceil((double) page.total() / page.size());
        return new PageResponse<>(
                page.content().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.total(),
                totalPages
        );
    }
}
