package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.ForgotPasswordCommand;
import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.dto.RegisterGestoraCommand;
import com.smxplore.users.application.dto.RegisterGuideCommand;
import com.smxplore.users.application.dto.RegisterProviderCommand;
import com.smxplore.users.application.dto.RegisterTouristCommand;
import com.smxplore.users.application.dto.ResetPasswordCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.application.port.in.LoginUseCase;
import com.smxplore.users.application.port.in.RegisterGestoraUseCase;
import com.smxplore.users.application.port.in.RegisterGuideUseCase;
import com.smxplore.users.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.application.port.in.ResetPasswordUseCase;
import com.smxplore.users.application.port.in.VerifyEmailUseCase;
import com.smxplore.users.infrastructure.adapter.in.dto.ForgotPasswordRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.LoginRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegisterGestoraRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegisterGuideRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegisterProviderRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegisterTouristRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.ResetPasswordRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.VerifyEmailRequest;
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
    private final RegisterGuideUseCase registerGuide;
    private final RegisterGestoraUseCase registerGestora;
    private final LoginUseCase login;
    private final ForgotPasswordUseCase forgotPassword;
    private final ResetPasswordUseCase resetPassword;
    private final VerifyEmailUseCase verifyEmail;

    public AuthController(
            RegisterTouristUseCase registerTourist,
            RegisterProviderUseCase registerProvider,
            RegisterGuideUseCase registerGuide,
            RegisterGestoraUseCase registerGestora,
            LoginUseCase login,
            ForgotPasswordUseCase forgotPassword,
            ResetPasswordUseCase resetPassword,
            VerifyEmailUseCase verifyEmail) {
        this.registerTourist = registerTourist;
        this.registerProvider = registerProvider;
        this.registerGuide = registerGuide;
        this.registerGestora = registerGestora;
        this.login = login;
        this.forgotPassword = forgotPassword;
        this.resetPassword = resetPassword;
        this.verifyEmail = verifyEmail;
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
                        request.websiteUrl(),
                        request.startOperationDate()))
                .map(user -> ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
                        .body(user));
    }

    @PostMapping("/register/guia")
    public Mono<ResponseEntity<UserView>> registerGuide(@Valid @RequestBody RegisterGuideRequest request) {
        return registerGuide
                .register(new RegisterGuideCommand(
                        request.fullName(),
                        request.email(),
                        request.phone(),
                        request.password(),
                        request.dniType(),
                        request.dniNumber(),
                        request.license(),
                        request.startOperationDate()))
                .map(user -> ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
                        .body(user));
    }

    @PostMapping("/register/gestora")
    public Mono<ResponseEntity<UserView>> registerGestora(@Valid @RequestBody RegisterGestoraRequest request) {
        return registerGestora
                .register(new RegisterGestoraCommand(
                        request.fullName(),
                        request.email(),
                        request.phone(),
                        request.password(),
                        request.nit(),
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

    /**
     * MS01-18: confirma la propiedad del correo con el token enviado al registrarse. Es público porque el usuario aún
     * puede no tener sesión (por ejemplo, si abrió el enlace en otro dispositivo).
     */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return verifyEmail.verify(request.token());
    }
}
