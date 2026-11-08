package com.smxplore.users.application.dto;

import java.util.UUID;

/** Contenido de un desafío MFA ya validado: a quién pertenece, la versión de sesión y el identificador de un solo uso. */
public record MfaChallengeContent(UUID userId, long authVersion, UUID jti) {}
