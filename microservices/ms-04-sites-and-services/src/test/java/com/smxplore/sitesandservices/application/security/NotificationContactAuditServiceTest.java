package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.notification.NotificationContactService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.audit.SecurityAuditEntry;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SecurityAuditRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS04-09: cambiar el correo de contacto exige confirmacion explicita y deja auditoria con el
 * valor anterior y el nuevo.
 */
class NotificationContactAuditServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final Actor ADMIN = new Actor(UUID.randomUUID(), UserRole.ADMIN, "Admin");
    private static final Actor MANAGER = new Actor(
            UUID.randomUUID(), UserRole.DESTINATION_MANAGER, "Gestora");

    private final NotificationContactRepositoryPort contactRepository =
            mock(NotificationContactRepositoryPort.class);
    private final SecurityAuditRepositoryPort auditRepository = mock(SecurityAuditRepositoryPort.class);

    private final NotificationContactService service = new NotificationContactService(
            contactRepository, auditRepository, new CatalogAccess(), CLOCK);

    @BeforeEach
    void defaultLookups() {
        // El servicio evalua los argumentos de Mono.then de forma inmediata: sin este stub el
        // mock devolveria null incluso en las rutas que fallan antes de consultar el contacto.
        when(contactRepository.findByUserId(TARGET_ID))
                .thenReturn(Mono.just(Optional.empty()));
    }

    @Test
    void requiresExplicitConfirmation() {
        StepVerifier.create(service.upsert(TARGET_ID, "nuevo@example.com", false, ADMIN))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidCatalogException.class)
                        .hasMessageContaining("explicit confirmation"))
                .verify();

        verify(contactRepository, never()).upsert(any());
    }

    @Test
    void nonAdminsCannotChangeContacts() {
        StepVerifier.create(service.upsert(TARGET_ID, "nuevo@example.com", true, MANAGER))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class))
                .verify();
    }

    @Test
    void upsertAuditsThePreviousAndNewEmail() {
        when(contactRepository.findByUserId(TARGET_ID))
                .thenReturn(Mono.just(Optional.of(contact("viejo@example.com"))));
        when(contactRepository.upsert(any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(auditRepository.append(any())).thenReturn(Mono.empty());

        NotificationContact saved = service
                .upsert(TARGET_ID, "nuevo@example.com", true, ADMIN)
                .block();

        assertThat(saved).isNotNull();
        assertThat(saved.email()).isEqualTo("nuevo@example.com");
        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(auditRepository).append(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("NOTIFICATION_CONTACT_UPSERT");
        assertThat(captor.getValue().detail())
                .contains("oldEmail=viejo@example.com")
                .contains("newEmail=nuevo@example.com");
    }

    @Test
    void deleteAuditsTheRemovedEmail() {
        when(contactRepository.findByUserId(TARGET_ID))
                .thenReturn(Mono.just(Optional.of(contact("viejo@example.com"))));
        when(contactRepository.deleteByUserId(TARGET_ID)).thenReturn(Mono.empty());
        when(auditRepository.append(any())).thenReturn(Mono.empty());

        service.delete(TARGET_ID, ADMIN).block();

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(auditRepository).append(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("NOTIFICATION_CONTACT_DELETE");
        assertThat(captor.getValue().detail()).contains("oldEmail=viejo@example.com");
    }

    private static NotificationContact contact(String email) {
        return NotificationContact.builder()
                .userId(TARGET_ID)
                .email(email)
                .updatedAt(CLOCK.instant())
                .build();
    }
}
