package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

/**
 * Límites para las listas expuestas por la API: evita consultas sin acotar.
 */
public final class PaginationLimits {

    public static final int DEFAULT = 50;
    public static final int MAX = 200;

    private PaginationLimits() {
    }

    public static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX);
    }
}
