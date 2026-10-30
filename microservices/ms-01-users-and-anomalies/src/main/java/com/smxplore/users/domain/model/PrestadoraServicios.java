package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PrestadoraServicios extends Usuario {

    private static final int MAX_WEBSITE_LENGTH = 255;

    private final String nit;
    private String businessType;
    private ProviderRegistrationStatus registrationStatus;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String websiteUrl;
    private final LocalDate startOperationDate;

    private PrestadoraServicios(
            UUID id,
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
            String websiteUrl,
            LocalDate startOperationDate) {
        super(id, fullName, email, phone, passwordHash, status, authVersion, signedUpAt, updatedAt, deletedAt);
        this.nit = nit;
        this.businessType = normalize(businessType);
        this.registrationStatus =
                registrationStatus == null ? ProviderRegistrationStatus.PENDIENTE : registrationStatus;
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
        this.websiteUrl = normalizeWebsite(websiteUrl);
        this.startOperationDate = startOperationDate;
    }

    public static PrestadoraServicios register(
            UUID id,
            String fullName,
            Email email,
            String phone,
            PasswordHash passwordHash,
            String nit,
            String businessType,
            BigDecimal latitude,
            BigDecimal longitude,
            String websiteUrl,
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
                websiteUrl,
                startOperationDate);
    }

    public static PrestadoraServicios rehydrate(
            UUID id,
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
            String websiteUrl,
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
                websiteUrl,
                startOperationDate);
    }

    public void updateBusinessDetails(
            String businessType, BigDecimal latitude, BigDecimal longitude, String websiteUrl, Instant now) {
        BigDecimal newLatitude = latitude != null ? latitude : this.latitude;
        BigDecimal newLongitude = longitude != null ? longitude : this.longitude;
        validateCoordinates(newLatitude, newLongitude);
        if (businessType != null) {
            this.businessType = normalize(businessType);
        }
        if (websiteUrl != null) {
            this.websiteUrl = normalizeWebsite(websiteUrl);
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
        return Role.SERVICE_PROVIDER;
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

    public String websiteUrl() {
        return websiteUrl;
    }

    public LocalDate startOperationDate() {
        return startOperationDate;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeWebsite(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.length() > MAX_WEBSITE_LENGTH) {
            throw new ValidationException("El sitio web no puede superar los " + MAX_WEBSITE_LENGTH + " caracteres");
        }
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!supportedScheme || uri.getHost() == null) {
                throw new ValidationException(
                        "El sitio web debe ser una URL válida que empiece por http:// o https://");
            }
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El sitio web debe ser una URL válida que empiece por http:// o https://");
        }
        return candidate;
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
