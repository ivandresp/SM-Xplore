package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderProfile(
        String nit,
        String businessType,
        ProviderRegistrationStatus registrationStatus,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate startOperationDate)
        implements Profile {

    public ProviderProfile {
        if (registrationStatus == null) {
            registrationStatus = ProviderRegistrationStatus.PENDIENTE;
        }
        businessType = normalize(businessType);
        validateCoordinates(latitude, longitude);
    }

    @Override
    public Role role() {
        return Role.PRESTADORA_SERVICIOS;
    }

    public ProviderProfile withRegistrationStatus(ProviderRegistrationStatus newStatus) {
        return new ProviderProfile(nit, businessType, newStatus, latitude, longitude, startOperationDate);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude != null
                && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                        || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new ValidationException("La latitud debe estar entre -90 y 90");
        }
        if (longitude != null
                && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                        || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new ValidationException("La longitud debe estar entre -180 y 180");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new ValidationException("La latitud y la longitud deben informarse juntas");
        }
    }
}
