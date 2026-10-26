package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.Usuario;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de perfil que pueden exponerse sin autenticación. A diferencia de {@link RoleDetailsView}, no incluye
 * documentos de identidad ni información administrativa.
 */
public sealed interface PublicProfileDetails permits PublicProfileDetails.Guide, PublicProfileDetails.Provider {

    static PublicProfileDetails from(Usuario user) {
        return switch (user) {
            case Guia guide -> new Guide(guide.license(), guide.startOperationDate());
            case PrestadoraServicios provider ->
                new Provider(
                        provider.nit(),
                        provider.businessType(),
                        provider.latitude(),
                        provider.longitude(),
                        provider.startOperationDate());
            default -> throw new IllegalStateException("El rol no tiene un perfil público");
        };
    }

    record Guide(String license, LocalDate startOperationDate) implements PublicProfileDetails {}

    record Provider(
            String nit, String businessType, BigDecimal latitude, BigDecimal longitude, LocalDate startOperationDate)
            implements PublicProfileDetails {}
}
