package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.model.AdminProfile;
import com.smxplore.users.identity.domain.model.DestinationManagerProfile;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.GuideProfile;
import com.smxplore.users.identity.domain.model.Profile;
import com.smxplore.users.identity.domain.model.ProviderProfile;
import com.smxplore.users.identity.domain.model.TouristProfile;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.identity.domain.model.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository users;
    private final TouristProfileJpaRepository tourists;
    private final GuideProfileJpaRepository guides;
    private final ProviderProfileJpaRepository providers;
    private final DestinationManagerProfileJpaRepository destinationManagers;
    private final AdminProfileJpaRepository admins;
    private final UserPersistenceMapper mapper = new UserPersistenceMapper();

    public UserRepositoryAdapter(
            UserJpaRepository users,
            TouristProfileJpaRepository tourists,
            GuideProfileJpaRepository guides,
            ProviderProfileJpaRepository providers,
            DestinationManagerProfileJpaRepository destinationManagers,
            AdminProfileJpaRepository admins) {
        this.users = users;
        this.tourists = tourists;
        this.guides = guides;
        this.providers = providers;
        this.destinationManagers = destinationManagers;
        this.admins = admins;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return users.findById(id.value()).map(this::load);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(Email email) {
        return users.findByEmail(email.value()).map(this::load);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return users.existsByEmail(email.value());
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity incoming = mapper.toEntity(user);
        UserJpaEntity stored = users.findById(user.id().value()).orElse(null);
        if (stored == null) {
            users.saveAndFlush(incoming);
        } else {
            stored.updateFrom(incoming);
            users.saveAndFlush(stored);
        }
        saveProfile(user);
        return user;
    }

    private User load(UserJpaEntity entity) {
        return mapper.toDomain(entity, loadProfile(entity));
    }

    private Profile loadProfile(UserJpaEntity entity) {
        return switch (entity.getRole()) {
            case TURISTA ->
                tourists.findById(entity.getId())
                        .map(mapper::toTouristProfile)
                        .orElseThrow(() -> missingProfile("turista", entity.getId()));
            case GUIA ->
                guides.findById(entity.getId())
                        .map(mapper::toGuideProfile)
                        .orElseThrow(() -> missingProfile("guía", entity.getId()));
            case PRESTADORA_SERVICIOS ->
                providers
                        .findById(entity.getId())
                        .map(mapper::toProviderProfile)
                        .orElseThrow(() -> missingProfile("prestadora", entity.getId()));
            case GESTORA_DESTINOS ->
                destinationManagers
                        .findById(entity.getId())
                        .map(mapper::toDestinationManagerProfile)
                        .orElseThrow(() -> missingProfile("gestora", entity.getId()));
            case ADMIN ->
                admins.findById(entity.getId())
                        .map(mapper::toAdminProfile)
                        .orElseThrow(() -> missingProfile("administrador", entity.getId()));
        };
    }

    private void saveProfile(User user) {
        switch (user.profile()) {
            case TouristProfile profile -> upsertTourist(user.id(), profile);
            case GuideProfile profile -> upsertGuide(user.id(), profile);
            case ProviderProfile profile -> upsertProvider(user.id(), profile);
            case DestinationManagerProfile profile -> upsertDestinationManager(user.id(), profile);
            case AdminProfile profile -> upsertAdmin(user.id(), profile);
        }
    }

    private void upsertTourist(UserId userId, TouristProfile profile) {
        TouristProfileJpaEntity incoming = mapper.toTouristEntity(userId, profile);
        TouristProfileJpaEntity stored = tourists.findById(userId.value()).orElse(null);
        if (stored == null) {
            tourists.save(incoming);
        } else {
            stored.updateFrom(incoming);
            tourists.save(stored);
        }
    }

    private void upsertGuide(UserId userId, GuideProfile profile) {
        GuideProfileJpaEntity incoming = mapper.toGuideEntity(userId, profile);
        GuideProfileJpaEntity stored = guides.findById(userId.value()).orElse(null);
        if (stored == null) {
            guides.save(incoming);
        } else {
            stored.updateFrom(incoming);
            guides.save(stored);
        }
    }

    private void upsertProvider(UserId userId, ProviderProfile profile) {
        ProviderProfileJpaEntity incoming = mapper.toProviderEntity(userId, profile);
        ProviderProfileJpaEntity stored = providers.findById(userId.value()).orElse(null);
        if (stored == null) {
            providers.save(incoming);
        } else {
            stored.updateFrom(incoming);
            providers.save(stored);
        }
    }

    private void upsertDestinationManager(UserId userId, DestinationManagerProfile profile) {
        DestinationManagerProfileJpaEntity incoming = mapper.toDestinationManagerEntity(userId, profile);
        DestinationManagerProfileJpaEntity stored =
                destinationManagers.findById(userId.value()).orElse(null);
        if (stored == null) {
            destinationManagers.save(incoming);
        } else {
            stored.updateFrom(incoming);
            destinationManagers.save(stored);
        }
    }

    private void upsertAdmin(UserId userId, AdminProfile profile) {
        AdminProfileJpaEntity incoming = mapper.toAdminEntity(userId, profile);
        AdminProfileJpaEntity stored = admins.findById(userId.value()).orElse(null);
        if (stored == null) {
            admins.save(incoming);
        } else {
            stored.updateFrom(incoming);
            admins.save(stored);
        }
    }

    private static IllegalStateException missingProfile(String role, java.util.UUID userId) {
        return new IllegalStateException("No se encontró el perfil de " + role + " para el usuario " + userId);
    }
}
