package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.domain.model.AdminProfile;
import com.smxplore.users.identity.domain.model.DestinationManagerProfile;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.GuideProfile;
import com.smxplore.users.identity.domain.model.PasswordHash;
import com.smxplore.users.identity.domain.model.Profile;
import com.smxplore.users.identity.domain.model.ProviderProfile;
import com.smxplore.users.identity.domain.model.TouristProfile;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.identity.domain.model.UserId;

final class UserPersistenceMapper {

    UserJpaEntity toEntity(User user) {
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

    User toDomain(UserJpaEntity entity, Profile profile) {
        return User.rehydrate(
                new UserId(entity.getId()),
                entity.getFullName(),
                new Email(entity.getEmail()),
                entity.getPhone(),
                new PasswordHash(entity.getPasswordHash()),
                profile,
                entity.getStatus(),
                entity.getAuthVersion(),
                entity.getSignedUpAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    TouristProfileJpaEntity toTouristEntity(UserId userId, TouristProfile profile) {
        return new TouristProfileJpaEntity(
                userId.value(),
                profile.dniType(),
                profile.dniNumber(),
                profile.preferredLanguage(),
                profile.birthDate(),
                profile.nationality());
    }

    GuideProfileJpaEntity toGuideEntity(UserId userId, GuideProfile profile) {
        return new GuideProfileJpaEntity(
                userId.value(),
                profile.dniType(),
                profile.dniNumber(),
                profile.license(),
                profile.startOperationDate());
    }

    ProviderProfileJpaEntity toProviderEntity(UserId userId, ProviderProfile profile) {
        return new ProviderProfileJpaEntity(
                userId.value(),
                profile.nit(),
                profile.businessType(),
                profile.registrationStatus(),
                profile.latitude(),
                profile.longitude(),
                profile.startOperationDate());
    }

    DestinationManagerProfileJpaEntity toDestinationManagerEntity(UserId userId, DestinationManagerProfile profile) {
        return new DestinationManagerProfileJpaEntity(userId.value(), profile.nit(), profile.startOperationDate());
    }

    AdminProfileJpaEntity toAdminEntity(UserId userId, AdminProfile profile) {
        return new AdminProfileJpaEntity(
                userId.value(), profile.dniType(), profile.dniNumber(), profile.lastAccessDate());
    }

    TouristProfile toTouristProfile(TouristProfileJpaEntity entity) {
        return new TouristProfile(
                entity.getDniType(),
                entity.getDniNumber(),
                entity.getPreferredLanguage(),
                entity.getBirthDate(),
                entity.getNationality());
    }

    GuideProfile toGuideProfile(GuideProfileJpaEntity entity) {
        return new GuideProfile(
                entity.getDniType(), entity.getDniNumber(), entity.getLicense(), entity.getStartOperationDate());
    }

    ProviderProfile toProviderProfile(ProviderProfileJpaEntity entity) {
        return new ProviderProfile(
                entity.getNit(),
                entity.getBusinessType(),
                entity.getRegistrationStatus(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getStartOperationDate());
    }

    DestinationManagerProfile toDestinationManagerProfile(DestinationManagerProfileJpaEntity entity) {
        return new DestinationManagerProfile(entity.getNit(), entity.getStartOperationDate());
    }

    AdminProfile toAdminProfile(AdminProfileJpaEntity entity) {
        return new AdminProfile(entity.getDniType(), entity.getDniNumber(), entity.getLastAccessDate());
    }
}
