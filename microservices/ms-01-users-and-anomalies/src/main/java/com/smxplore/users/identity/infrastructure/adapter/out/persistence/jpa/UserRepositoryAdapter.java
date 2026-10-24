package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.model.Admin;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.GestoraDestinos;
import com.smxplore.users.identity.domain.model.Guia;
import com.smxplore.users.identity.domain.model.PrestadoraServicios;
import com.smxplore.users.identity.domain.model.Turista;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
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
    public Optional<Usuario> findById(UserId id) {
        return users.findById(id.value()).map(this::load);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> findByEmail(Email email) {
        return users.findByEmail(email.value()).map(this::load);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return users.existsByEmail(email.value());
    }

    @Override
    @Transactional
    public Usuario save(Usuario user) {
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

    private Usuario load(UserJpaEntity entity) {
        return switch (entity.getRole()) {
            case TURISTA ->
                tourists.findById(entity.getId())
                        .map(profile -> (Usuario) mapper.toDomain(entity, profile))
                        .orElseThrow(() -> missingProfile("turista", entity.getId()));
            case GUIA ->
                guides.findById(entity.getId())
                        .map(profile -> (Usuario) mapper.toDomain(entity, profile))
                        .orElseThrow(() -> missingProfile("guía", entity.getId()));
            case PRESTADORA_SERVICIOS ->
                providers
                        .findById(entity.getId())
                        .map(profile -> (Usuario) mapper.toDomain(entity, profile))
                        .orElseThrow(() -> missingProfile("prestadora", entity.getId()));
            case GESTORA_DESTINOS ->
                destinationManagers
                        .findById(entity.getId())
                        .map(profile -> (Usuario) mapper.toDomain(entity, profile))
                        .orElseThrow(() -> missingProfile("gestora", entity.getId()));
            case ADMIN ->
                admins.findById(entity.getId())
                        .map(profile -> (Usuario) mapper.toDomain(entity, profile))
                        .orElseThrow(() -> missingProfile("administrador", entity.getId()));
        };
    }

    private void saveProfile(Usuario user) {
        switch (user) {
            case Turista tourist -> upsertTourist(user.id(), tourist);
            case Guia guide -> upsertGuide(user.id(), guide);
            case PrestadoraServicios provider -> upsertProvider(user.id(), provider);
            case GestoraDestinos manager -> upsertDestinationManager(user.id(), manager);
            case Admin admin -> upsertAdmin(user.id(), admin);
        }
    }

    private void upsertTourist(UserId userId, Turista user) {
        TouristProfileJpaEntity incoming = mapper.toProfileEntity(userId, user);
        TouristProfileJpaEntity stored = tourists.findById(userId.value()).orElse(null);
        if (stored == null) {
            tourists.save(incoming);
        } else {
            stored.updateFrom(incoming);
            tourists.save(stored);
        }
    }

    private void upsertGuide(UserId userId, Guia user) {
        GuideProfileJpaEntity incoming = mapper.toProfileEntity(userId, user);
        GuideProfileJpaEntity stored = guides.findById(userId.value()).orElse(null);
        if (stored == null) {
            guides.save(incoming);
        } else {
            stored.updateFrom(incoming);
            guides.save(stored);
        }
    }

    private void upsertProvider(UserId userId, PrestadoraServicios user) {
        ProviderProfileJpaEntity incoming = mapper.toProfileEntity(userId, user);
        ProviderProfileJpaEntity stored = providers.findById(userId.value()).orElse(null);
        if (stored == null) {
            providers.save(incoming);
        } else {
            stored.updateFrom(incoming);
            providers.save(stored);
        }
    }

    private void upsertDestinationManager(UserId userId, GestoraDestinos user) {
        DestinationManagerProfileJpaEntity incoming = mapper.toProfileEntity(userId, user);
        DestinationManagerProfileJpaEntity stored =
                destinationManagers.findById(userId.value()).orElse(null);
        if (stored == null) {
            destinationManagers.save(incoming);
        } else {
            stored.updateFrom(incoming);
            destinationManagers.save(stored);
        }
    }

    private void upsertAdmin(UserId userId, Admin user) {
        AdminProfileJpaEntity incoming = mapper.toProfileEntity(userId, user);
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
