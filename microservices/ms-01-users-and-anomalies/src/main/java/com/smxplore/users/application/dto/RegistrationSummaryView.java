package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.RegistrationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la cola de solicitudes de alta.
 *
 * <p>Expone solo lo necesario para decidir: quién solicita, cómo contactarlo y su identificador profesional
 * ({@code license} para guías, {@code nit} para prestadoras). Nada de documentos, direcciones ni credenciales.
 */
public record RegistrationSummaryView(
        UUID id,
        String role,
        String fullName,
        String email,
        String license,
        String nit,
        Instant signedUpAt,
        RegistrationStatus registrationStatus) {}
