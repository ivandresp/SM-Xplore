package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

/**
 * Límites para las listas expuestas por la API: evita consultas sin acotar.
 */
public final class PaginationLimits {

    public static final int DEFAULT = 50;
    public static final int MAX = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationLimits() {
    }

    public static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX);
    }

    public static void validatePage(int page) {
        if (page < 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "page must be >= 0");
        }
    }

    public static void validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
