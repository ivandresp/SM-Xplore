package com.smxplore.users.application.dto;

import java.util.UUID;

/** Versión de autenticación que otros servicios comparan con el claim {@code ver} del JWT. */
public record AuthVersionView(UUID userId, long authVersion) {}
