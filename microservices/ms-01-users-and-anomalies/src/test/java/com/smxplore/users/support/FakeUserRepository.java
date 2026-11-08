package com.smxplore.users.support;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserStatistics;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Usuario;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Mono;

public final class FakeUserRepository implements UserRepository {

    private final Map<UUID, Usuario> users = new ConcurrentHashMap<>();
    private final List<Usuario> saved = new CopyOnWriteArrayList<>();

    public FakeUserRepository seed(Usuario user) {
        users.put(user.id(), user);
        return this;
    }

    public List<Usuario> saved() {
        return saved;
    }

    public Usuario lastSaved() {
        return saved.get(saved.size() - 1);
    }

    @Override
    public Mono<Usuario> findById(UUID id) {
        return Mono.justOrEmpty(users.get(id));
    }

    @Override
    public Mono<Usuario> findByEmail(Email email) {
        return Mono.justOrEmpty(users.values().stream()
                .filter(user -> user.email().equals(email))
                .findFirst());
    }

    @Override
    public Mono<Boolean> existsByEmail(Email email) {
        return findByEmail(email).hasElement();
    }

    @Override
    public Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria) {
        return unsupported();
    }

    @Override
    public Mono<Long> countActiveByRole(Role role) {
        return Mono.just(1L);
    }

    @Override
    public Mono<Long> countAll() {
        return Mono.just((long) users.size());
    }

    @Override
    public Mono<UserStatistics> statistics() {
        return unsupported();
    }

    @Override
    public Mono<Usuario> save(Usuario user) {
        users.put(user.id(), user);
        saved.add(user);
        return Mono.just(user);
    }

    @Override
    public Mono<Void> deleteProfile(Role role, UUID userId) {
        return Mono.empty();
    }

    private static <T> Mono<T> unsupported() {
        return Mono.error(new UnsupportedOperationException());
    }
}
