package com.smxplore.sitesandservices.domain.model.reservation;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class ReservationItem {

    public static final int MAX_SERVICE_NAME_LENGTH = 150;
    public static final int MAX_PROVIDER_NAME_LENGTH = 150;
    public static final int MAX_SERVICE_TYPE_LENGTH = 50;

    private final UUID reservationItemId;
    private final UUID serviceId;
    private final String serviceName;
    private final UUID providerId;
    private final String providerName;
    private final String serviceType;
    @Builder.Default
    private final BigDecimal unitPrice = BigDecimal.ZERO;
    @Builder.Default
    private final int people = 1;
    private final BigDecimal total;
    private final int position;

    public void validate() {
        validateReservationItemId(reservationItemId);
        validateService(serviceId, serviceName);
        validateProvider(providerId, providerName);
        validateServiceType(serviceType);
        validateUnitPrice(unitPrice);
        validatePeople(people);
        validateTotal(unitPrice, people, total);
        validatePosition(position);
    }

    public static BigDecimal calculateTotal(BigDecimal unitPrice, int people) {
        BigDecimal safePrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return safePrice.multiply(BigDecimal.valueOf(people)).setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateReservationItemId(UUID reservationItemId) {
        if (reservationItemId == null) {
            throw new InvalidCatalogException("reservationItemId is required");
        }
    }

    private static void validateService(UUID serviceId, String serviceName) {
        if (serviceId == null) {
            throw new InvalidCatalogException("reservation item serviceId is required");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new InvalidCatalogException("reservation item serviceName is required");
        }
        requireMaxLength(serviceName, MAX_SERVICE_NAME_LENGTH, "reservation item serviceName");
    }

    private static void validateProvider(UUID providerId, String providerName) {
        if (providerId == null) {
            throw new InvalidCatalogException("reservation item providerId is required");
        }
        if (providerName == null || providerName.isBlank()) {
            throw new InvalidCatalogException("reservation item providerName is required");
        }
        requireMaxLength(providerName, MAX_PROVIDER_NAME_LENGTH, "reservation item providerName");
    }

    private static void validateServiceType(String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            throw new InvalidCatalogException("reservation item serviceType is required");
        }
        requireMaxLength(serviceType, MAX_SERVICE_TYPE_LENGTH, "reservation item serviceType");
    }

    private static void requireMaxLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new InvalidCatalogException(
                    field + " cannot exceed " + maxLength + " characters");
        }
    }

    private static void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new InvalidCatalogException("reservation item unitPrice is required");
        }
        if (unitPrice.signum() < 0) {
            throw new InvalidCatalogException("reservation item unitPrice must be >= 0");
        }
    }

    private static void validatePeople(int people) {
        if (people < 1) {
            throw new InvalidCatalogException("reservation item people must be >= 1");
        }
    }

    private static void validateTotal(BigDecimal unitPrice, int people, BigDecimal total) {
        if (total == null) {
            throw new InvalidCatalogException("reservation item total is required");
        }
        if (total.signum() < 0) {
            throw new InvalidCatalogException("reservation item total must be >= 0");
        }
        if (total.compareTo(calculateTotal(unitPrice, people)) != 0) {
            throw new InvalidCatalogException(
                    "reservation item total must equal unitPrice multiplied by people");
        }
    }

    private static void validatePosition(int position) {
        if (position < 0) {
            throw new InvalidCatalogException("reservation item position must be >= 0");
        }
    }
}
