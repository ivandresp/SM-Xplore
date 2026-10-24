package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.application.dto.ForgotPasswordCommand;
import com.smxplore.users.identity.application.dto.LoginCommand;
import com.smxplore.users.identity.application.dto.LoginResult;
import com.smxplore.users.identity.application.dto.RegisterProviderCommand;
import com.smxplore.users.identity.application.dto.RegisterTouristCommand;
import com.smxplore.users.identity.application.dto.ResetPasswordCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.identity.application.port.in.LoginUseCase;
import com.smxplore.users.identity.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.identity.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.identity.application.port.in.ResetPasswordUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterTouristUseCase registerTourist;
    private final RegisterProviderUseCase registerProvider;
    private final LoginUseCase login;
    private final ForgotPasswordUseCase forgotPassword;
    private final ResetPasswordUseCase resetPassword;

    public AuthController(
            RegisterTouristUseCase registerTourist,
            RegisterProviderUseCase registerProvider,
            LoginUseCase login,
            ForgotPasswordUseCase forgotPassword,
            ResetPasswordUseCase resetPassword) {
        this.registerTourist = registerTourist;
        this.registerProvider = registerProvider;
        this.login = login;
        this.forgotPassword = forgotPassword;
        this.resetPassword = resetPassword;
    }

    @PostMapping("/register/turista")
    public ResponseEntity<UserView> registerTourist(@Valid @RequestBody RegisterTouristRequest request) {
        UserView user = registerTourist.register(new RegisterTouristCommand(
                request.fullName(),
                request.email(),
                request.phone(),
                request.password(),
                request.dniType(),
                request.dniNumber(),
                request.preferredLanguage(),
                request.birthDate(),
                request.nationality()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PostMapping("/register/prestadora")
    public ResponseEntity<UserView> registerProvider(@Valid @RequestBody RegisterProviderRequest request) {
        UserView user = registerProvider.register(new RegisterProviderCommand(
                request.fullName(),
                request.email(),
                request.phone(),
                request.password(),
                request.nit(),
                request.businessType(),
                request.latitude(),
                request.longitude(),
                request.startOperationDate()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request) {
        return login.login(new LoginCommand(request.email(), request.password()));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        forgotPassword.handle(new ForgotPasswordCommand(request.email()));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.reset(new ResetPasswordCommand(request.token(), request.newPassword()));
    }
}
