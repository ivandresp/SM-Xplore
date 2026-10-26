package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.ForgotPasswordCommand;
import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.dto.RegisterProviderCommand;
import com.smxplore.users.application.dto.RegisterTouristCommand;
import com.smxplore.users.application.dto.ResetPasswordCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.application.port.in.LoginUseCase;
import com.smxplore.users.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.application.port.in.ResetPasswordUseCase;
import com.smxplore.users.infrastructure.adapter.in.dto.ForgotPasswordRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.LoginRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegisterProviderRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegisterTouristRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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
    public Mono<ResponseEntity<UserView>> registerTourist(@Valid @RequestBody RegisterTouristRequest request) {
        return registerTourist
                .register(new RegisterTouristCommand(
                        request.fullName(),
                        request.email(),
                        request.phone(),
                        request.password(),
                        request.dniType(),
                        request.dniNumber(),
                        request.preferredLanguage(),
                        request.birthDate(),
                        request.nationality()))
                .map(user -> ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
                        .body(user));
    }

    @PostMapping("/register/prestadora")
    public Mono<ResponseEntity<UserView>> registerProvider(@Valid @RequestBody RegisterProviderRequest request) {
        return registerProvider
                .register(new RegisterProviderCommand(
                        request.fullName(),
                        request.email(),
                        request.phone(),
                        request.password(),
                        request.nit(),
                        request.businessType(),
                        request.latitude(),
                        request.longitude(),
                        request.startOperationDate()))
                .map(user -> ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
                        .body(user));
    }

    @PostMapping("/login")
    public Mono<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return login.login(new LoginCommand(request.email(), request.password()));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return forgotPassword.handle(new ForgotPasswordCommand(request.email()));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return resetPassword.reset(new ResetPasswordCommand(request.token(), request.newPassword()));
    }
}
