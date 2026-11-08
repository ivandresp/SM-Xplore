package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inscripción de un nuevo factor TOTP. Se exige la contraseña actual para que un token robado no permita inscribir un
 * autenticador controlado por el atacante.
 */
public record EnrollMfaRequest(@NotBlank String password) {}
