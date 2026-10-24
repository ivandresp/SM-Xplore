package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.AdminProfile;
import com.smxplore.users.identity.domain.model.DestinationManagerProfile;
import com.smxplore.users.identity.domain.model.GuideProfile;
import com.smxplore.users.identity.domain.model.Profile;
import com.smxplore.users.identity.domain.model.ProviderProfile;
import com.smxplore.users.identity.domain.model.TouristProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public sealed interface ProfileView
        permits ProfileView.Tourist,
                ProfileView.Guide,
                ProfileView.Provider,
                ProfileView.DestinationManager,
                ProfileView.Admin {

    static ProfileView from(Profile profile) {
        return switch (profile) {
            case TouristProfile tourist ->
                new Tourist(
                        tourist.dniType(),
                        tourist.dniNumber(),
                        tourist.preferredLanguage(),
                        tourist.birthDate(),
                        tourist.nationality());
            case GuideProfile guide ->
                new Guide(guide.dniType(), guide.dniNumber(), guide.license(), guide.startOperationDate());
            case ProviderProfile provider ->
                new Provider(
                        provider.nit(),
                        provider.businessType(),
                        provider.registrationStatus().name(),
                        provider.latitude(),
                        provider.longitude(),
                        provider.startOperationDate());
            case DestinationManagerProfile manager ->
                new DestinationManager(manager.nit(), manager.startOperationDate());
            case AdminProfile admin -> new Admin(admin.dniType(), admin.dniNumber(), admin.lastAccessDate());
        };
    }

    record Tourist(String dniType, String dniNumber, String preferredLanguage, LocalDate birthDate, String nationality)
            implements ProfileView {}

    record Guide(String dniType, String dniNumber, String license, LocalDate startOperationDate)
            implements ProfileView {}

    record Provider(
            String nit,
            String businessType,
            String registrationStatus,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate)
            implements ProfileView {}

    record DestinationManager(String nit, LocalDate startOperationDate) implements ProfileView {}

    record Admin(String dniType, String dniNumber, Instant lastAccessDate) implements ProfileView {}
}
