package com.smxplore.users.domain.service;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.RoleProfileData;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;

/**
 * Migra un usuario a otro rol conservando su identidad, credenciales y estado, pero reconstruyendo el subtipo
 * correcto con los datos de perfil exigidos por el nuevo rol.
 */
public final class RoleMigration {

    private RoleMigration() {}

    public static Usuario changeRole(Usuario current, Role newRole, RoleProfileData profile, Instant now) {
        if (newRole == null) {
            throw new ValidationException("El rol es obligatorio");
        }
        if (current.role() == newRole) {
            throw new ValidationException("El usuario ya tiene el rol " + newRole.code());
        }
        if (current.status() == UserStatus.ELIMINADO) {
            throw new ValidationException("La cuenta está eliminada y no puede cambiar de rol");
        }
        if (profile == null) {
            throw new ValidationException("Los datos del nuevo rol son obligatorios");
        }

        long nextAuthVersion = current.authVersion() + 1;
        return switch (newRole) {
            case TOURIST -> new TuristaProfile(current, profile, now, nextAuthVersion).build();
            case GUIDE -> new GuideProfile(current, profile, now, nextAuthVersion).build();
            case SERVICE_PROVIDER -> new ProviderProfile(current, profile, now, nextAuthVersion).build();
            case DESTINATION_MANAGER -> new DestinationManagerProfile(current, profile, now, nextAuthVersion).build();
            case ADMIN -> new AdminProfile(current, profile, now, nextAuthVersion).build();
        };
    }

    private record TuristaProfile(Usuario current, RoleProfileData data, Instant now, long authVersion) {

        Turista build() {
            return Turista.rehydrate(
                    current.id(),
                    current.fullName(),
                    current.email(),
                    current.phone(),
                    current.passwordHash(),
                    current.status(),
                    authVersion,
                    current.signedUpAt(),
                    now,
                    current.deletedAt(),
                    require(data.dniType(), "El tipo de documento es obligatorio para un turista"),
                    require(data.dniNumber(), "El número de documento es obligatorio para un turista"),
                    data.preferredLanguage(),
                    data.birthDate(),
                    data.nationality());
        }
    }

    private record GuideProfile(Usuario current, RoleProfileData data, Instant now, long authVersion) {

        Guia build() {
            return Guia.rehydrate(
                    current.id(),
                    current.fullName(),
                    current.email(),
                    current.phone(),
                    current.passwordHash(),
                    current.status(),
                    authVersion,
                    current.signedUpAt(),
                    now,
                    current.deletedAt(),
                    require(data.dniType(), "El tipo de documento es obligatorio para un guía"),
                    require(data.dniNumber(), "El número de documento es obligatorio para un guía"),
                    require(data.license(), "La licencia es obligatoria para un guía"),
                    data.startOperationDate());
        }
    }

    private record ProviderProfile(Usuario current, RoleProfileData data, Instant now, long authVersion) {

        PrestadoraServicios build() {
            return PrestadoraServicios.rehydrate(
                    current.id(),
                    current.fullName(),
                    current.email(),
                    current.phone(),
                    current.passwordHash(),
                    current.status(),
                    authVersion,
                    current.signedUpAt(),
                    now,
                    current.deletedAt(),
                    require(data.nit(), "El NIT es obligatorio para una prestadora de servicios"),
                    data.businessType(),
                    ProviderRegistrationStatus.PENDIENTE,
                    data.latitude(),
                    data.longitude(),
                    data.websiteUrl(),
                    data.startOperationDate());
        }
    }

    private record DestinationManagerProfile(Usuario current, RoleProfileData data, Instant now, long authVersion) {

        GestoraDestinos build() {
            return GestoraDestinos.rehydrate(
                    current.id(),
                    current.fullName(),
                    current.email(),
                    current.phone(),
                    current.passwordHash(),
                    current.status(),
                    authVersion,
                    current.signedUpAt(),
                    now,
                    current.deletedAt(),
                    require(data.nit(), "El NIT es obligatorio para una gestora de destinos"),
                    data.startOperationDate());
        }
    }

    private record AdminProfile(Usuario current, RoleProfileData data, Instant now, long authVersion) {

        Admin build() {
            return Admin.rehydrate(
                    current.id(),
                    current.fullName(),
                    current.email(),
                    current.phone(),
                    current.passwordHash(),
                    current.status(),
                    authVersion,
                    current.signedUpAt(),
                    now,
                    current.deletedAt(),
                    blankToNull(data.dniType()),
                    blankToNull(data.dniNumber()),
                    null);
        }
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
