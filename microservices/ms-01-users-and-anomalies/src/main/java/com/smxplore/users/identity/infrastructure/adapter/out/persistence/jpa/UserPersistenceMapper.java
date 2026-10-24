package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.domain.model.Admin;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.GestoraDestinos;
import com.smxplore.users.identity.domain.model.Guia;
import com.smxplore.users.identity.domain.model.PasswordHash;
import com.smxplore.users.identity.domain.model.PrestadoraServicios;
import com.smxplore.users.identity.domain.model.Turista;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;

final class UserPersistenceMapper {

    UserJpaEntity toEntity(Usuario user) {
        return new UserJpaEntity(
                user.id().value(),
                user.fullName(),
                user.email().value(),
                user.phone(),
                user.passwordHash().value(),
                user.role(),
                user.status(),
                user.authVersion(),
                user.signedUpAt(),
                user.updatedAt(),
                user.deletedAt());
    }

    Turista toDomain(UserJpaEntity entity, TouristProfileJpaEntity profile) {
        return Turista.rehydrate(
                new UserId(entity.getId()),
                entity.getFullName(),
                new Email(entity.getEmail()),
                entity.getPhone(),
                new PasswordHash(entity.getPasswordHash()),
                entity.getStatus(),
                entity.getAuthVersion(),
                entity.getSignedUpAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                profile.getDniType(),
                profile.getDniNumber(),
                profile.getPreferredLanguage(),
                profile.getBirthDate(),
                profile.getNationality());
    }

    Guia toDomain(UserJpaEntity entity, GuideProfileJpaEntity profile) {
        return Guia.rehydrate(
                new UserId(entity.getId()),
                entity.getFullName(),
                new Email(entity.getEmail()),
                entity.getPhone(),
                new PasswordHash(entity.getPasswordHash()),
                entity.getStatus(),
                entity.getAuthVersion(),
                entity.getSignedUpAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                profile.getDniType(),
                profile.getDniNumber(),
                profile.getLicense(),
                profile.getStartOperationDate());
    }

    PrestadoraServicios toDomain(UserJpaEntity entity, ProviderProfileJpaEntity profile) {
        return PrestadoraServicios.rehydrate(
                new UserId(entity.getId()),
                entity.getFullName(),
                new Email(entity.getEmail()),
                entity.getPhone(),
                new PasswordHash(entity.getPasswordHash()),
                entity.getStatus(),
                entity.getAuthVersion(),
                entity.getSignedUpAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                profile.getNit(),
                profile.getBusinessType(),
                profile.getRegistrationStatus(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getStartOperationDate());
    }

    GestoraDestinos toDomain(UserJpaEntity entity, DestinationManagerProfileJpaEntity profile) {
        return GestoraDestinos.rehydrate(
                new UserId(entity.getId()),
                entity.getFullName(),
                new Email(entity.getEmail()),
                entity.getPhone(),
                new PasswordHash(entity.getPasswordHash()),
                entity.getStatus(),
                entity.getAuthVersion(),
                entity.getSignedUpAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                profile.getNit(),
                profile.getStartOperationDate());
    }

    Admin toDomain(UserJpaEntity entity, AdminProfileJpaEntity profile) {
        return Admin.rehydrate(
                new UserId(entity.getId()),
                entity.getFullName(),
                new Email(entity.getEmail()),
                entity.getPhone(),
                new PasswordHash(entity.getPasswordHash()),
                entity.getStatus(),
                entity.getAuthVersion(),
                entity.getSignedUpAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                profile.getDniType(),
                profile.getDniNumber(),
                profile.getLastAccessDate());
    }

    TouristProfileJpaEntity toProfileEntity(UserId userId, Turista user) {
        return new TouristProfileJpaEntity(
                userId.value(),
                user.dniType(),
                user.dniNumber(),
                user.preferredLanguage(),
                user.birthDate(),
                user.nationality());
    }

    GuideProfileJpaEntity toProfileEntity(UserId userId, Guia user) {
        return new GuideProfileJpaEntity(
                userId.value(), user.dniType(), user.dniNumber(), user.license(), user.startOperationDate());
    }

    ProviderProfileJpaEntity toProfileEntity(UserId userId, PrestadoraServicios user) {
        return new ProviderProfileJpaEntity(
                userId.value(),
                user.nit(),
                user.businessType(),
                user.registrationStatus(),
                user.latitude(),
                user.longitude(),
                user.startOperationDate());
    }

    DestinationManagerProfileJpaEntity toProfileEntity(UserId userId, GestoraDestinos user) {
        return new DestinationManagerProfileJpaEntity(userId.value(), user.nit(), user.startOperationDate());
    }

    AdminProfileJpaEntity toProfileEntity(UserId userId, Admin user) {
        return new AdminProfileJpaEntity(userId.value(), user.dniType(), user.dniNumber(), user.lastAccessDate());
    }
}
