package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.UpdateProfileCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.Admin;
import com.smxplore.users.identity.domain.model.GestoraDestinos;
import com.smxplore.users.identity.domain.model.Guia;
import com.smxplore.users.identity.domain.model.PrestadoraServicios;
import com.smxplore.users.identity.domain.model.Turista;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;

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
            Usuario user = users.findById(userId).orElseThrow(UserNotFoundException::new);
            String fullName =
                    command.fullName() == null || command.fullName().isBlank() ? user.fullName() : command.fullName();
            String phone = resolvePhone(command.phone(), user.phone());
            Instant now = clock.instant();

            user.updateContact(fullName, phone, now);
            updateRoleDetails(user, command, now);

            return UserView.from(users.save(user));
        });
    }

    private static void updateRoleDetails(Usuario user, UpdateProfileCommand command, Instant now) {
        switch (user) {
            case Turista tourist ->
                tourist.updateDetails(command.preferredLanguage(), command.birthDate(), command.nationality(), now);
            case Guia guide -> guide.updateLicense(command.license(), now);
            case PrestadoraServicios provider ->
                provider.updateBusinessDetails(command.businessType(), command.latitude(), command.longitude(), now);
            case GestoraDestinos ignored -> {
                // Only common contact data is editable for destination managers.
            }
            case Admin ignored -> {
                // Only common contact data is editable for administrators.
            }
        }
    }

    private static String resolvePhone(String rawPhone, String currentPhone) {
        if (rawPhone == null) {
            return currentPhone;
        }
        if (rawPhone.isBlank()) {
            return null;
        }
        return rawPhone;
    }
}
