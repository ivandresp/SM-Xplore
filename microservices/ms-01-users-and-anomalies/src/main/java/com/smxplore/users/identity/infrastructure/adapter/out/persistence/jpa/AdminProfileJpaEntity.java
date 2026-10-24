package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_profiles")
class AdminProfileJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "dni_type", length = 20)
    private String dniType;

    @Column(name = "dni_number", length = 30)
    private String dniNumber;

    @Column(name = "last_access_date")
    private Instant lastAccessDate;

    protected AdminProfileJpaEntity() {}

    AdminProfileJpaEntity(UUID userId, String dniType, String dniNumber, Instant lastAccessDate) {
        this.userId = userId;
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.lastAccessDate = lastAccessDate;
    }

    void updateFrom(AdminProfileJpaEntity source) {
        this.dniType = source.dniType;
        this.dniNumber = source.dniNumber;
        this.lastAccessDate = source.lastAccessDate;
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

    Instant getLastAccessDate() {
        return lastAccessDate;
    }
}
