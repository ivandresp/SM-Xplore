package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tourist_profiles")
class TouristProfileJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "dni_type", nullable = false, length = 20)
    private String dniType;

    @Column(name = "dni_number", nullable = false, length = 30)
    private String dniNumber;

    @Column(name = "preferred_language", length = 50)
    private String preferredLanguage;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 80)
    private String nationality;

    protected TouristProfileJpaEntity() {}

    TouristProfileJpaEntity(
            UUID userId,
            String dniType,
            String dniNumber,
            String preferredLanguage,
            LocalDate birthDate,
            String nationality) {
        this.userId = userId;
        this.dniType = dniType;
        this.dniNumber = dniNumber;
        this.preferredLanguage = preferredLanguage;
        this.birthDate = birthDate;
        this.nationality = nationality;
    }

    void updateFrom(TouristProfileJpaEntity source) {
        this.dniType = source.dniType;
        this.dniNumber = source.dniNumber;
        this.preferredLanguage = source.preferredLanguage;
        this.birthDate = source.birthDate;
        this.nationality = source.nationality;
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

    String getPreferredLanguage() {
        return preferredLanguage;
    }

    LocalDate getBirthDate() {
        return birthDate;
    }

    String getNationality() {
        return nationality;
    }
}
