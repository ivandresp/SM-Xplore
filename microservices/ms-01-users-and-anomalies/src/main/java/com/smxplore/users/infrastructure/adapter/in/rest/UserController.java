package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.ChangePasswordCommand;
import com.smxplore.users.application.dto.UpdateProfileCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.infrastructure.adapter.in.dto.ChangePasswordRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetCurrentUserUseCase getCurrentUser;
    private final UpdateProfileUseCase updateProfile;
    private final ChangeOwnPasswordUseCase changeOwnPassword;

    public UserController(
            GetCurrentUserUseCase getCurrentUser,
            UpdateProfileUseCase updateProfile,
            ChangeOwnPasswordUseCase changeOwnPassword) {
        this.getCurrentUser = getCurrentUser;
        this.updateProfile = updateProfile;
        this.changeOwnPassword = changeOwnPassword;
    }

    @GetMapping("/me")
    public Mono<UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return getCurrentUser.get(userId(jwt));
    }

    @PatchMapping("/me")
    public Mono<UserView> updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return updateProfile.update(
                userId(jwt),
                new UpdateProfileCommand(
                        request.fullName(),
                        request.phone(),
                        request.preferredLanguage(),
                        request.birthDate(),
                        request.nationality(),
                        request.businessType(),
                        request.latitude(),
                        request.longitude(),
                        request.license()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        return changeOwnPassword.change(
                userId(jwt), new ChangePasswordCommand(request.currentPassword(), request.newPassword()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
