package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserContactView;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserStatistics;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Verifica la proyección de la cuenta a {@link UserContactView} y el 404 cuando no existe. */
class GetUserContactServiceTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final StubUserRepository users = new StubUserRepository();
    private final GetUserContactService service = new GetUserContactService(users);

    @Test
    void projectsTheContactDataOfTheAccount() {
        users.user = turista();

        StepVerifier.create(service.get(USER_ID))
                .assertNext(contact -> {
                    assertThat(contact.userId()).isEqualTo(USER_ID);
                    assertThat(contact.email()).isEqualTo("ada@example.com");
                    assertThat(contact.fullName()).isEqualTo("Ada Turista");
                    assertThat(contact.role()).isEqualTo("TOURIST");
                    assertThat(contact.phone()).isEqualTo("3001234567");
                    assertThat(contact.active()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void exposesINACTIVEAccountsAsInactiveButStillReturnsTheContact() {
        Turista blocked = Turista.rehydrate(
                USER_ID,
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash-de-prueba"),
                UserStatus.BLOQUEADO,
                3L,
                NOW,
                NOW,
                null,
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia");
        users.user = blocked;

        StepVerifier.create(service.get(USER_ID))
                .assertNext(contact -> assertThat(contact.active()).isFalse())
                .verifyComplete();
    }

    @Test
    void failsWithUserNotFoundWhenTheAccountDoesNotExist() {
        users.user = null;

        StepVerifier.create(service.get(USER_ID))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    private static Turista turista() {
        return Turista.register(
                USER_ID,
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash-de-prueba"),
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia",
                NOW);
    }

    private static final class StubUserRepository implements UserRepository {

        private Usuario user;

        @Override
        public Mono<Usuario> findById(UUID id) {
            Usuario current = user;
            return current != null && current.id().equals(id) ? Mono.just(current) : Mono.empty();
        }

        @Override
        public Mono<Usuario> findByEmail(Email email) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> existsByEmail(Email email) {
            return Mono.just(false);
        }

        @Override
        public Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria) {
            return Mono.error(new UnsupportedOperationException("No usado en esta prueba"));
        }

        @Override
        public Mono<Long> countActiveByRole(Role role) {
            return Mono.just(0L);
        }

        @Override
        public Mono<UserStatistics> statistics() {
            return Mono.error(new UnsupportedOperationException("No usado en esta prueba"));
        }

        @Override
        public Mono<Usuario> save(Usuario user) {
            this.user = user;
            return Mono.just(user);
        }

        @Override
        public Mono<Void> deleteProfile(Role role, UUID userId) {
            return Mono.empty();
        }
    }
}
