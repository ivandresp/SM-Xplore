package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public sealed interface RoleDetailsView
        permits RoleDetailsView.Tourist,
                RoleDetailsView.Guide,
                RoleDetailsView.Provider,
                RoleDetailsView.DestinationManager,
                RoleDetailsView.Administrator {

    static RoleDetailsView from(Usuario usuario) {
        return switch (usuario) {
            case Turista tourist ->
                new Tourist(
                        tourist.dniType(),
                        tourist.dniNumber(),
                        tourist.preferredLanguage(),
                        tourist.birthDate(),
                        tourist.nationality());
            case Guia guide ->
                new Guide(guide.dniType(), guide.dniNumber(), guide.license(), guide.startOperationDate());
            case PrestadoraServicios provider ->
                new Provider(
                        provider.nit(),
                        provider.businessType(),
                        provider.registrationStatus().name(),
                        provider.latitude(),
                        provider.longitude(),
                        provider.startOperationDate());
            case GestoraDestinos manager -> new DestinationManager(manager.nit(), manager.startOperationDate());
            case Admin admin -> new Administrator(admin.dniType(), admin.dniNumber(), admin.lastAccessDate());
        };
    }

    record Tourist(String dniType, String dniNumber, String preferredLanguage, LocalDate birthDate, String nationality)
            implements RoleDetailsView {}

    record Guide(String dniType, String dniNumber, String license, LocalDate startOperationDate)
            implements RoleDetailsView {}

    record Provider(
            String nit,
            String businessType,
            String registrationStatus,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate startOperationDate)
            implements RoleDetailsView {}

    record DestinationManager(String nit, LocalDate startOperationDate) implements RoleDetailsView {}

    record Administrator(String dniType, String dniNumber, Instant lastAccessDate) implements RoleDetailsView {}
}
