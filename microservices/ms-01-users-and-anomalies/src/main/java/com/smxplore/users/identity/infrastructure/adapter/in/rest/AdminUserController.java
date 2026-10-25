package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.identity.application.dto.CreateInternalUserCommand;
import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.dto.ReviewProviderCommand;
import com.smxplore.users.identity.application.dto.SortOrder;
import com.smxplore.users.identity.application.dto.UserSearchCriteria;
import com.smxplore.users.identity.application.dto.UserSummaryView;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.ChangeUserStatusUseCase;
import com.smxplore.users.identity.application.port.in.CreateInternalUserUseCase;
import com.smxplore.users.identity.application.port.in.GetUserDetailUseCase;
import com.smxplore.users.identity.application.port.in.ReviewProviderUseCase;
import com.smxplore.users.identity.application.port.in.SearchUsersUseCase;
import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.UserStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final SearchUsersUseCase searchUsers;
    private final GetUserDetailUseCase getUserDetail;
    private final ChangeUserStatusUseCase changeUserStatus;
    private final ReviewProviderUseCase reviewProvider;
    private final CreateInternalUserUseCase createInternalUser;

    public AdminUserController(
            SearchUsersUseCase searchUsers,
            GetUserDetailUseCase getUserDetail,
            ChangeUserStatusUseCase changeUserStatus,
            ReviewProviderUseCase reviewProvider,
            CreateInternalUserUseCase createInternalUser) {
        this.searchUsers = searchUsers;
        this.getUserDetail = getUserDetail;
        this.changeUserStatus = changeUserStatus;
        this.reviewProvider = reviewProvider;
        this.createInternalUser = createInternalUser;
    }

    @GetMapping
    public PageResult<UserSummaryView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) ProviderRegistrationStatus providerStatus,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) SortOrder sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchUsers.search(
                new UserSearchCriteria(q, role, status, providerStatus, sortBy, sortOrder, page, size));
    }

    @GetMapping("/{id}")
    public UserView detail(@PathVariable UUID id) {
        return getUserDetail.get(new UserId(id));
    }

    @PostMapping
    public ResponseEntity<UserView> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInternalUserRequest request) {
        UserView user = createInternalUser.create(
                actorId(jwt),
                new CreateInternalUserCommand(
                        request.role(),
                        request.fullName(),
                        request.email(),
                        request.phone(),
                        request.dniType(),
                        request.dniNumber(),
                        request.license(),
                        request.nit(),
                        request.startOperationDate()));
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.id()))
                .body(user);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        changeUserStatus.change(
                actorId(jwt), new UserId(id), new ChangeUserStatusCommand(request.status(), request.reason()));
    }

    @PostMapping("/{id}/provider-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewProvider(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProviderReviewRequest request) {
        reviewProvider.review(
                actorId(jwt), new UserId(id), new ReviewProviderCommand(request.decision(), request.reason()));
    }

    private static UserId actorId(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }
}
