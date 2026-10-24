package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.application.dto.UpdateProfileCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.identity.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.identity.domain.model.UserId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetCurrentUserUseCase getCurrentUser;
    private final UpdateProfileUseCase updateProfile;

    public UserController(GetCurrentUserUseCase getCurrentUser, UpdateProfileUseCase updateProfile) {
        this.getCurrentUser = getCurrentUser;
        this.updateProfile = updateProfile;
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

    private static UserId userId(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }
}
