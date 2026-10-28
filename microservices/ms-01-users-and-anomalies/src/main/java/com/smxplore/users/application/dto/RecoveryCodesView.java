package com.smxplore.users.application.dto;

import java.util.List;

/**
 * Códigos de recuperación recién generados. Sólo se devuelven una vez; en la base de datos quedan como hashes.
 */
public record RecoveryCodesView(List<String> codes) {}
