package com.smxplore.users.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de perfil que se exigen o aceptan al cambiar el rol de un usuario.
 *
 * <p>Es un único contrato para los cinco roles: cada tipo de usuario valida y usa únicamente los campos que le
 * corresponden.
 */
public record RoleProfileData(
        String dniType,
        String dniNumber,
        String preferredLanguage,
        LocalDate birthDate,
        String nationality,
        String license,
        String nit,
        String businessType,
        BigDecimal latitude,
        BigDecimal longitude,
        String websiteUrl,
        LocalDate startOperationDate) {}
