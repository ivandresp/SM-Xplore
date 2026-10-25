package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.application.dto.ChangePasswordCommand;
import com.smxplore.users.identity.application.dto.UpdateProfileCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.identity.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.identity.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.identity.domain.model.UserId;
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
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return getCurrentUser.get(userId(jwt));
    }

    @PatchMapping("/me")
    public UserView updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
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
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        changeOwnPassword.change(
                userId(jwt), new ChangePasswordCommand(request.currentPassword(), request.newPassword()));
    }

    private static UserId userId(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }
}
