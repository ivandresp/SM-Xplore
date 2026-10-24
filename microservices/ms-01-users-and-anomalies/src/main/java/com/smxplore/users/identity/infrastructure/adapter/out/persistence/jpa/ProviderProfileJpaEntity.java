package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_profiles")
class ProviderProfileJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String nit;

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 30)
    private ProviderRegistrationStatus registrationStatus;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "start_operation_date")
    private LocalDate startOperationDate;

    protected ProviderProfileJpaEntity() {}

    ProviderProfileJpaEntity(
            UUID userId,
            String nit,
            String businessType,
            ProviderRegistrationStatus registrationStatus,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate) {
        this.userId = userId;
        this.nit = nit;
        this.businessType = businessType;
        this.registrationStatus = registrationStatus;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startOperationDate = startOperationDate;
    }

    void updateFrom(ProviderProfileJpaEntity source) {
        this.nit = source.nit;
        this.businessType = source.businessType;
        this.registrationStatus = source.registrationStatus;
        this.latitude = source.latitude;
        this.longitude = source.longitude;
        this.startOperationDate = source.startOperationDate;
    }

    UUID getUserId() {
        return userId;
    }

    String getNit() {
        return nit;
    }

    String getBusinessType() {
        return businessType;
    }

    ProviderRegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    BigDecimal getLatitude() {
        return latitude;
    }

    BigDecimal getLongitude() {
        return longitude;
    }

    LocalDate getStartOperationDate() {
        return startOperationDate;
    }
}
