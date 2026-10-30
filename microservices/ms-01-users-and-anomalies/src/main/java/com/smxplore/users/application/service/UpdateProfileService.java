package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.UpdateProfileCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

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
    public Mono<UserView> update(UUID userId, UpdateProfileCommand command) {
        return transactions.execute(Mono.defer(() -> users.findById(userId)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .flatMap(user -> {
                    String fullName =
                            command.fullName() == null || command.fullName().isBlank()
                                    ? user.fullName()
                                    : command.fullName();
                    String phone = resolvePhone(command.phone(), user.phone());
                    Instant now = clock.instant();

                    user.updateContact(fullName, phone, now);
                    updateRoleDetails(user, command, now);

                    return users.save(user);
                })
                .map(UserView::from)));
    }

    private static void updateRoleDetails(Usuario user, UpdateProfileCommand command, Instant now) {
        switch (user) {
            case Turista tourist ->
                tourist.updateDetails(command.preferredLanguage(), command.birthDate(), command.nationality(), now);
            case Guia guide -> guide.updateLicense(command.license(), now);
            case PrestadoraServicios provider ->
                provider.updateBusinessDetails(
                        command.businessType(), command.latitude(), command.longitude(), command.websiteUrl(), now);
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
