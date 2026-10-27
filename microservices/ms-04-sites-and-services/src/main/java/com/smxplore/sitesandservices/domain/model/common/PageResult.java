package com.smxplore.sitesandservices.domain.model.common;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long total
) {

    public PageResult {
        validateContent(content);
        validatePage(page);
        validateSize(size);
        validateTotal(total);
        content = List.copyOf(content);
    }

    private static void validateContent(List<?> content) {
        if (content == null) {
            throw new IllegalArgumentException("page content is required");
        }
        if (content.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("page content cannot contain null values");
        }
    }

    private static void validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
    }

    private static void validateSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
    }

    private static void validateTotal(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
    }
}
