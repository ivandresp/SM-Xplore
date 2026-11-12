package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.RegistrationPage;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.in.ListRegistrationsUseCase;
import com.smxplore.users.application.port.in.ReviewRegistrationUseCase;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.infrastructure.adapter.in.dto.RegistrationReviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * MS01-B8: cola de solicitudes de alta para la gestora de destinos.
 *
 * <p>Deliberadamente fuera de {@code /api/v1/admin/users}: la gestora necesita revisar altas, no administrar cuentas
 * (cambiar estados, roles o borrar usuarios). Este contrato expone solo la cola y la decisión.
 */
@RestController
@RequestMapping("/api/v1/registrations")
@PreAuthorize("hasAnyRole('ADMIN','DESTINATION_MANAGER')")
public class RegistrationController {

    private final ListRegistrationsUseCase listRegistrations;
    private final ReviewRegistrationUseCase reviewRegistration;

    public RegistrationController(
            ListRegistrationsUseCase listRegistrations, ReviewRegistrationUseCase reviewRegistration) {
        this.listRegistrations = listRegistrations;
        this.reviewRegistration = reviewRegistration;
    }

    @GetMapping
    public Mono<RegistrationPage> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Role requestedRole = parseRole(role);
        // Las altas de gestoras solo las consulta un administrador: son las únicas que ninguna gestora puede
        // revisar, así que listarlas en la cola de trabajo de las gestoras sería exponer datos sin poder decidir.
        if (requestedRole == Role.DESTINATION_MANAGER && !"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ForbiddenOperationException("Solo un administrador puede consultar las altas de gestoras");
        }
        return listRegistrations.search(new RegistrationSearchCriteria(requestedRole, status, page, size));
    }

    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return reviewRegistration.reviewRegistration(
                UUID.fromString(jwt.getSubject()),
                id,
                new ReviewRegistrationCommand(request.decision(), request.reason()));
    }

    private static Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return Role.fromCode(role);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El rol no es válido");
        }
    }
}
