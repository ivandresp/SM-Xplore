package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "signed_up_at", nullable = false)
    private Instant signedUpAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserJpaEntity() {}

    UserJpaEntity(
            UUID id,
            String fullName,
            String email,
            String phone,
            String passwordHash,
            Role role,
            UserStatus status,
            long authVersion,
            Instant signedUpAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.authVersion = authVersion;
        this.signedUpAt = signedUpAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    void updateFrom(UserJpaEntity source) {
        this.fullName = source.fullName;
        this.email = source.email;
        this.phone = source.phone;
        this.passwordHash = source.passwordHash;
        this.role = source.role;
        this.status = source.status;
        this.authVersion = source.authVersion;
        this.updatedAt = source.updatedAt;
        this.deletedAt = source.deletedAt;
    }

    UUID getId() {
        return id;
    }

    String getFullName() {
        return fullName;
    }

    String getEmail() {
        return email;
    }

    String getPhone() {
        return phone;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }

    UserStatus getStatus() {
        return status;
    }

    long getAuthVersion() {
        return authVersion;
    }

    Instant getSignedUpAt() {
        return signedUpAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }
}
