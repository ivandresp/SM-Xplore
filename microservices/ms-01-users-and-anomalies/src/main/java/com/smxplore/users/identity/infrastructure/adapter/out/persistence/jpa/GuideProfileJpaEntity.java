package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "guide_profiles")
class GuideProfileJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "dni_type", nullable = false, length = 20)
    private String dniType;

    @Column(name = "dni_number", nullable = false, length = 30)
    private String dniNumber;

    @Column(nullable = false, length = 50)
    private String license;

    @Column(name = "start_operation_date")
    private LocalDate startOperationDate;

    protected GuideProfileJpaEntity() {}

    GuideProfileJpaEntity(UUID userId, String dniType, String dniNumber, String license, LocalDate startOperationDate) {
        this.userId = userId;
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.license = license;
        this.startOperationDate = startOperationDate;
    }

    void updateFrom(GuideProfileJpaEntity source) {
        this.dniType = source.dniType;
        this.dniNumber = source.dniNumber;
        this.license = source.license;
        this.startOperationDate = source.startOperationDate;
    }

    UUID getUserId() {
        return userId;
    }

    String getDniType() {
        return dniType;
    }

    String getDniNumber() {
        return dniNumber;
    }

    String getLicense() {
        return license;
    }

    LocalDate getStartOperationDate() {
        return startOperationDate;
    }
}
