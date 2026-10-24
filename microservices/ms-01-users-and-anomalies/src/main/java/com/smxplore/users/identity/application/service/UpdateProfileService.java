package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.UpdateProfileCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.AdminProfile;
import com.smxplore.users.identity.domain.model.DestinationManagerProfile;
import com.smxplore.users.identity.domain.model.FullName;
import com.smxplore.users.identity.domain.model.GuideProfile;
import com.smxplore.users.identity.domain.model.Phone;
import com.smxplore.users.identity.domain.model.Profile;
import com.smxplore.users.identity.domain.model.ProviderProfile;
import com.smxplore.users.identity.domain.model.TouristProfile;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Clock;

public final class UpdateProfileService implements UpdateProfileUseCase {

    private final UserRepository users;
    private final TransactionPort transactions;
    private final Clock clock;

    public UpdateProfileService(UserRepository users, TransactionPort transactions, Clock clock) {
        this.users = users;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public UserView update(UserId userId, UpdateProfileCommand command) {
        return transactions.execute(() -> {
            User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
            FullName fullName = command.fullName() == null || command.fullName().isBlank()
                    ? user.fullName()
                    : new FullName(command.fullName());
            Phone phone = resolvePhone(command.phone(), user.phone());
            Profile profile = updatedProfile(user.profile(), command);

            User updated = user.updateProfile(fullName, phone, profile, clock.instant());
            return UserView.from(users.save(updated));
        });
    }

    private static Phone resolvePhone(String rawPhone, Phone currentPhone) {
        if (rawPhone == null) {
            return currentPhone;
        }
        if (rawPhone.isBlank()) {
            return null;
        }
        return new Phone(rawPhone);
    }

    private static Profile updatedProfile(Profile current, UpdateProfileCommand command) {
        return switch (current) {
            case TouristProfile tourist ->
                new TouristProfile(
                        tourist.dni(),
                        command.preferredLanguage() == null ? tourist.preferredLanguage() : command.preferredLanguage(),
                        command.birthDate() == null ? tourist.birthDate() : command.birthDate(),
                        command.nationality() == null ? tourist.nationality() : command.nationality());
            case GuideProfile guide ->
                new GuideProfile(
                        guide.dni(),
                        command.license() == null ? guide.license() : command.license(),
                        guide.startOperationDate());
            case ProviderProfile provider ->
                new ProviderProfile(
                        provider.nit(),
                        command.businessType() == null ? provider.businessType() : command.businessType(),
                        provider.registrationStatus(),
                        command.latitude() == null ? provider.latitude() : command.latitude(),
                        command.longitude() == null ? provider.longitude() : command.longitude(),
                        provider.startOperationDate());
            case DestinationManagerProfile manager ->
                new DestinationManagerProfile(manager.nit(), manager.startOperationDate());
            case AdminProfile admin -> admin;
        };
    }
}
