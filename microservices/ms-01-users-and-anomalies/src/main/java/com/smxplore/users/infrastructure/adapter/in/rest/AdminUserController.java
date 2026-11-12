package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.ChangeUserRoleCommand;
import com.smxplore.users.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.application.dto.CreateInternalUserCommand;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.dto.SortOrder;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.AdminResetPasswordUseCase;
import com.smxplore.users.application.port.in.ChangeUserRoleUseCase;
import com.smxplore.users.application.port.in.ChangeUserStatusUseCase;
import com.smxplore.users.application.port.in.CreateInternalUserUseCase;
import com.smxplore.users.application.port.in.DeleteUserUseCase;
import com.smxplore.users.application.port.in.GetUserDetailUseCase;
import com.smxplore.users.application.port.in.ReviewRegistrationUseCase;
import com.smxplore.users.application.port.in.SearchUsersUseCase;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.infrastructure.adapter.in.dto.ChangeUserRoleRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.ChangeUserStatusRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.CreateInternalUserRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegistrationReviewRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final SearchUsersUseCase searchUsers;
    private final GetUserDetailUseCase getUserDetail;
    private final ChangeUserStatusUseCase changeUserStatus;
    private final ChangeUserRoleUseCase changeUserRole;
    private final DeleteUserUseCase deleteUser;
    private final ReviewRegistrationUseCase reviewRegistration;
    private final CreateInternalUserUseCase createInternalUser;
    private final AdminResetPasswordUseCase adminResetPassword;

    public AdminUserController(
            SearchUsersUseCase searchUsers,
            GetUserDetailUseCase getUserDetail,
            ChangeUserStatusUseCase changeUserStatus,
            ChangeUserRoleUseCase changeUserRole,
            DeleteUserUseCase deleteUser,
            ReviewRegistrationUseCase reviewRegistration,
            CreateInternalUserUseCase createInternalUser,
            AdminResetPasswordUseCase adminResetPassword) {
        this.searchUsers = searchUsers;
        this.getUserDetail = getUserDetail;
        this.changeUserStatus = changeUserStatus;
        this.changeUserRole = changeUserRole;
        this.deleteUser = deleteUser;
        this.reviewRegistration = reviewRegistration;
        this.createInternalUser = createInternalUser;
        this.adminResetPassword = adminResetPassword;
    }

    @GetMapping
    public Mono<PageResult<UserSummaryView>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RegistrationStatus providerStatus,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) SortOrder sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchUsers.search(new UserSearchCriteria(
                q,
                role == null || role.isBlank() ? null : parseRole(role),
                status,
                providerStatus,
                sortBy,
                sortOrder,
                page,
                size));
    }

    @GetMapping("/{id}")
    public Mono<UserView> detail(@PathVariable UUID id) {
        return getUserDetail.get(id);
    }

    @PostMapping
    public Mono<ResponseEntity<UserView>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInternalUserRequest request) {
        return createInternalUser
                .create(
                        actorId(jwt),
                        new CreateInternalUserCommand(
                                parseRole(request.role()),
                                request.fullName(),
                                request.email(),
                                request.phone(),
                                request.dniType(),
                                request.dniNumber(),
                                request.license(),
                                request.nit(),
                                request.startOperationDate()))
                .map(user -> ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.id()))
                        .body(user));
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return changeUserStatus.change(
                actorId(jwt), id, new ChangeUserStatusCommand(request.status(), request.reason()));
    }

    @PatchMapping("/{id}/role")
    public Mono<UserView> changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserRoleRequest request) {
        return changeUserRole.change(
                actorId(jwt),
                id,
                new ChangeUserRoleCommand(
                        parseRole(request.role()),
                        request.dniType(),
                        request.dniNumber(),
                        request.preferredLanguage(),
                        request.birthDate(),
                        request.nationality(),
                        request.license(),
                        request.nit(),
                        request.businessType(),
                        request.latitude(),
                        request.longitude(),
                        request.websiteUrl(),
                        request.startOperationDate()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return deleteUser.delete(actorId(jwt), id);
    }

    @PostMapping("/{id}/provider-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reviewProvider(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return reviewRegistration.review(
                actorId(jwt), id, new ReviewRegistrationCommand(request.decision(), request.reason()));
    }

    /**
     * MS01-B8: una gestora aprueba o rechaza el alta de un guía. Reutiliza el mismo caso de uso y la misma máquina de
     * estados que la revisión de prestadoras; solo cambia la ruta pública y la acción auditada.
     */
    @PostMapping("/{id}/guide-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reviewGuide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return reviewRegistration.review(
                actorId(jwt), id, new ReviewRegistrationCommand(request.decision(), request.reason()));
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return adminResetPassword.reset(actorId(jwt), id);
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static Role parseRole(String role) {
        try {
            return Role.fromCode(role);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El rol no es válido");
        }
    }
}
