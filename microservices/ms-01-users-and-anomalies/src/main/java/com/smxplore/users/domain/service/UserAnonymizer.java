package com.smxplore.users.domain.service;

import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;

/**
 * Construye la versión anonimizada de una cuenta para una baja definitiva.
 *
 * <p>Conserva el identificador y las marcas de tiempo necesarias para la trazabilidad, pero elimina nombre,
 * correo, teléfono, documento, NIT, licencia y demás datos personales del usuario y de su perfil.
 */
public final class UserAnonymizer {

    private static final String DELETED_NAME = "Usuario eliminado";

    private UserAnonymizer() {}

    public static Usuario anonymize(
            Usuario current, Email anonymizedEmail, PasswordHash unusablePassword, Instant now) {
        String suffix = suffix(current);
        long authVersion = current.authVersion() + 1;

        return switch (current) {
            case Turista tourist ->
                Turista.rehydrate(
                        current.id(),
                        DELETED_NAME,
                        anonymizedEmail,
                        null,
                        unusablePassword,
                        UserStatus.ELIMINADO,
                        authVersion,
                        current.signedUpAt(),
                        now,
                        now,
                        "ANON",
                        suffix,
                        "es",
                        null,
                        null);
            case Guia guide ->
                Guia.rehydrate(
                        current.id(),
                        DELETED_NAME,
                        anonymizedEmail,
                        null,
                        unusablePassword,
                        UserStatus.ELIMINADO,
                        authVersion,
                        current.signedUpAt(),
                        now,
                        now,
                        "ANON",
                        suffix,
                        "ANON-" + current.id(),
                        null);
            case PrestadoraServicios provider ->
                PrestadoraServicios.rehydrate(
                        current.id(),
                        DELETED_NAME,
                        anonymizedEmail,
                        null,
                        unusablePassword,
                        UserStatus.ELIMINADO,
                        authVersion,
                        current.signedUpAt(),
                        now,
                        now,
                        suffix,
                        null,
                        ProviderRegistrationStatus.RECHAZADO,
                        null,
                        null,
                        null,
                        null);
            case GestoraDestinos manager ->
                GestoraDestinos.rehydrate(
                        current.id(),
                        DELETED_NAME,
                        anonymizedEmail,
                        null,
                        unusablePassword,
                        UserStatus.ELIMINADO,
                        authVersion,
                        current.signedUpAt(),
                        now,
                        now,
                        suffix,
                        null);
            case Admin admin ->
                Admin.rehydrate(
                        current.id(),
                        DELETED_NAME,
                        anonymizedEmail,
                        null,
                        unusablePassword,
                        UserStatus.ELIMINADO,
                        authVersion,
                        current.signedUpAt(),
                        now,
                        now,
                        null,
                        null,
                        null);
        };
    }

    private static String suffix(Usuario user) {
        return "ANON-" + user.id().toString().replace("-", "").substring(0, 20);
    }
}
