package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.exceptions.catalog.InvalidCatalogFilterException;

/**
 * Límites para las listas expuestas por la API: evita consultas sin acotar.
 * <p>
 * El catálogo público no exige autenticación, así que la ventana de paginación y la búsqueda
 * también se acotan aquí: un {@code page} arbitrario multiplicaba el {@code OFFSET} y una
 * búsqueda de un carácter forzaba un recorrido completo de la tabla.
 */
public final class PaginationLimits {

    public static final int DEFAULT = 50;
    public static final int MAX = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /** Máximo desplazamiento admitido: {@code page * size}. */
    public static final long MAX_OFFSET = 10_000L;

    public static final int SEARCH_MIN_LENGTH = 3;
    public static final int SEARCH_MAX_LENGTH = 80;

    private PaginationLimits() {
    }

    public static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX);
    }

    public static void validatePage(int page) {
        if (page < 0) {
            throw new InvalidCatalogFilterException("page must be >= 0");
        }
    }

    public static void validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCatalogFilterException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    /** Valida página y tamaño juntos y acota el desplazamiento total. */
    public static void validatePageSize(int page, int size) {
        validatePage(page);
        validateSize(size);
        if ((long) page * size > MAX_OFFSET) {
            throw new InvalidCatalogFilterException("page * size must be <= " + MAX_OFFSET);
        }
    }

    /**
     * Normaliza y valida un filtro de texto libre. Devuelve el valor recortado o {@code null}
     * si no se envió; lanza 400 si viene vacío, fuera de rango o con comodines de LIKE.
     */
    public static String validateSearch(String search) {
        return validateSearch(search, "search");
    }

    public static String validateSearch(String search, String field) {
        if (search == null) {
            return null;
        }
        var trimmed = search.trim();
        if (trimmed.length() < SEARCH_MIN_LENGTH || trimmed.length() > SEARCH_MAX_LENGTH) {
            throw new InvalidCatalogFilterException(
                    field + " must be between " + SEARCH_MIN_LENGTH + " and " + SEARCH_MAX_LENGTH + " characters");
        }
        if (trimmed.indexOf('%') >= 0 || trimmed.indexOf('_') >= 0 || trimmed.indexOf('\\') >= 0) {
            throw new InvalidCatalogFilterException(field + " must not contain wildcard characters");
        }
        return trimmed;
    }
}
