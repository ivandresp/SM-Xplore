package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.dto.VerifyMfaCommand;
import com.smxplore.users.application.port.in.VerifyMfaLoginUseCase;
import com.smxplore.users.infrastructure.adapter.in.dto.VerifyMfaRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {

    private final VerifyMfaLoginUseCase verify;

    public MfaController(VerifyMfaLoginUseCase verify) {
        this.verify = verify;
    }

    @PostMapping("/verify")
    public Mono<LoginResult> verify(@Valid @RequestBody VerifyMfaRequest request) {
        return verify.verify(new VerifyMfaCommand(request.challengeToken(), request.code()));
    }
}
