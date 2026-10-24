package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PrestadoraServicios extends Usuario {

    private final String nit;
    private String businessType;
    private ProviderRegistrationStatus registrationStatus;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private final LocalDate startOperationDate;

    private PrestadoraServicios(
            UserId id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt,
            String nit,
            String businessType,
            ProviderRegistrationStatus registrationStatus,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.nit = nit;
        this.businessType = normalize(businessType);
        this.registrationStatus =
                registrationStatus == null ? ProviderRegistrationStatus.PENDIENTE : registrationStatus;
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
        this.startOperationDate = startOperationDate;
    }

    public static PrestadoraServicios register(
            UserId id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String nit,
            String businessType,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate,
            Instant now) {
        return new PrestadoraServicios(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                UserStatus.ACTIVO,
                0L,
                now,
                now,
                null,
                nit,
                businessType,
                ProviderRegistrationStatus.PENDIENTE,
                latitude,
                longitude,
                startOperationDate);
    }

    public static PrestadoraServicios rehydrate(
            UserId id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt,
            String nit,
            String businessType,
            ProviderRegistrationStatus registrationStatus,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate) {
        return new PrestadoraServicios(
                id,
                fullName,
                email,
                phone,
                passwordHash,
                status,
                authVersion,
                signedUpAt,
                updatedAt,
                deletedAt,
                nit,
                businessType,
                registrationStatus,
                latitude,
                longitude,
                startOperationDate);
    }

    public void updateBusinessDetails(String businessType, BigDecimal latitude, BigDecimal longitude, Instant now) {
        BigDecimal newLatitude = latitude != null ? latitude : this.latitude;
        BigDecimal newLongitude = longitude != null ? longitude : this.longitude;
        validateCoordinates(newLatitude, newLongitude);
        if (businessType != null) {
            this.businessType = normalize(businessType);
        }
        this.latitude = newLatitude;
        this.longitude = newLongitude;
        markUpdated(now);
    }

    public void changeRegistrationStatus(ProviderRegistrationStatus newStatus, Instant now) {
        this.registrationStatus = newStatus;
        incrementAuthVersion();
        markUpdated(now);
    }

    @Override
    public Role role() {
        return Role.PRESTADORA_SERVICIOS;
    }

    public String nit() {
        return nit;
    }

    public String businessType() {
        return businessType;
    }

    public ProviderRegistrationStatus registrationStatus() {
        return registrationStatus;
    }

    public BigDecimal latitude() {
        return latitude;
    }

    public BigDecimal longitude() {
        return longitude;
    }

    public LocalDate startOperationDate() {
        return startOperationDate;
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
