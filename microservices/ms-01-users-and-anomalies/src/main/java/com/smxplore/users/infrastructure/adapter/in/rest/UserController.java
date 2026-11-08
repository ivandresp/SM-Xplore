package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.AuthVersionView;
import com.smxplore.users.application.dto.ChangePasswordCommand;
import com.smxplore.users.application.dto.UpdateProfileCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.application.port.in.LogoutUseCase;
import com.smxplore.users.application.port.in.SendEmailVerificationUseCase;
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
import org.springframework.web.bind.annotation.PostMapping;
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
    private final LogoutUseCase logout;
    private final SendEmailVerificationUseCase sendEmailVerification;

    public UserController(
            GetCurrentUserUseCase getCurrentUser,
            UpdateProfileUseCase updateProfile,
            ChangeOwnPasswordUseCase changeOwnPassword,
            LogoutUseCase logout,
            SendEmailVerificationUseCase sendEmailVerification) {
        this.getCurrentUser = getCurrentUser;
        this.updateProfile = updateProfile;
        this.changeOwnPassword = changeOwnPassword;
        this.logout = logout;
        this.sendEmailVerification = sendEmailVerification;
    }

    @GetMapping("/me")
    public Mono<UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return getCurrentUser.get(userId(jwt));
    }

    @GetMapping("/me/auth-version")
    public Mono<AuthVersionView> authVersion(@AuthenticationPrincipal Jwt jwt) {
        return getCurrentUser.get(userId(jwt)).map(user -> new AuthVersionView(user.id(), user.authVersion()));
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
                        request.websiteUrl(),
                        request.license()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        return changeOwnPassword.change(
                userId(jwt), new ChangePasswordCommand(request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/me/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        return logout.logout(userId(jwt));
    }

    /**
     * MS01-18: reenvía el enlace de verificación de correo. Idempotente: si la cuenta ya está verificada no hace nada.
     */
    @PostMapping("/me/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> resendEmailVerification(@AuthenticationPrincipal Jwt jwt) {
        return sendEmailVerification.send(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
