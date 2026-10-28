package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.MfaEnrollmentView;
import com.smxplore.users.application.dto.MfaStatusView;
import com.smxplore.users.application.dto.RecoveryCodesView;
import com.smxplore.users.application.port.in.ConfirmMfaUseCase;
import com.smxplore.users.application.port.in.DisableMfaUseCase;
import com.smxplore.users.application.port.in.EnrollMfaUseCase;
import com.smxplore.users.application.port.in.GetMfaStatusUseCase;
import com.smxplore.users.application.port.in.RegenerateRecoveryCodesUseCase;
import com.smxplore.users.infrastructure.adapter.in.dto.ConfirmMfaRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.DisableMfaRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RegenerateRecoveryCodesRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users/me/mfa")
public class UserMfaController {

    private final GetMfaStatusUseCase getStatus;
    private final EnrollMfaUseCase enroll;
    private final ConfirmMfaUseCase confirm;
    private final DisableMfaUseCase disable;
    private final RegenerateRecoveryCodesUseCase regenerateRecoveryCodes;

    public UserMfaController(
            GetMfaStatusUseCase getStatus,
            EnrollMfaUseCase enroll,
            ConfirmMfaUseCase confirm,
            DisableMfaUseCase disable,
            RegenerateRecoveryCodesUseCase regenerateRecoveryCodes) {
        this.getStatus = getStatus;
        this.enroll = enroll;
        this.confirm = confirm;
        this.disable = disable;
        this.regenerateRecoveryCodes = regenerateRecoveryCodes;
    }

    @GetMapping
    public Mono<MfaStatusView> status(@AuthenticationPrincipal Jwt jwt) {
        return getStatus.status(userId(jwt));
    }

    @PostMapping("/enroll")
    public Mono<MfaEnrollmentView> enroll(@AuthenticationPrincipal Jwt jwt) {
        return enroll.enroll(userId(jwt));
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmMfaRequest request) {
        return confirm.confirm(userId(jwt), request.code());
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DisableMfaRequest request) {
        return disable.disable(userId(jwt), request.password(), request.code());
    }

    @PostMapping("/recovery-codes")
    public Mono<RecoveryCodesView> regenerateRecoveryCodes(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegenerateRecoveryCodesRequest request) {
        return regenerateRecoveryCodes.regenerate(userId(jwt), request.password(), request.code());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
