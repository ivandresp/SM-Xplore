package com.smxplore.sitesandservices.domain.model.service;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceFilter(
        UUID categoryId,
        UUID zoneId,
        UUID siteId,
        UUID providerId,
        ContentStatus status,
        String type,
        String search,
        BigDecimal priceMin,
        BigDecimal priceMax,
        int page,
        int size
) {

    /** MS04-06: cota superior de precio y de desplazamiento para listados publicos. */
    private static final BigDecimal MAX_PRICE = new BigDecimal("100000000");
    private static final int MAX_OFFSET = 10_000;

    public ServiceFilter {
        validatePage(page);
        validateSize(size);
        validatePagination(page, size);
        validatePrices(priceMin, priceMax);
    }

    private static void validatePage(int page) {
        if (page < 0) {
            throw new InvalidCatalogException("page must be >= 0");
        }
    }

    private static void validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new InvalidCatalogException("size must be between 1 and 100");
        }
    }

    private static void validatePagination(int page, int size) {
        if ((long) page * size > MAX_OFFSET) {
            throw new InvalidCatalogException("page and size are too large for this listing");
        }
    }

    private static void validatePrices(BigDecimal priceMin, BigDecimal priceMax) {
        if (priceMin != null) {
            if (priceMin.signum() < 0) {
                throw new InvalidCatalogException("priceMin must be >= 0");
            }
            if (priceMin.compareTo(MAX_PRICE) > 0) {
                throw new InvalidCatalogException("priceMin must be <= 100000000");
            }
        }
        if (priceMax != null) {
            if (priceMax.signum() < 0) {
                throw new InvalidCatalogException("priceMax must be >= 0");
            }
            if (priceMax.compareTo(MAX_PRICE) > 0) {
                throw new InvalidCatalogException("priceMax must be <= 100000000");
            }
        }
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new InvalidCatalogException("priceMin must be <= priceMax");
        }
    }
}
