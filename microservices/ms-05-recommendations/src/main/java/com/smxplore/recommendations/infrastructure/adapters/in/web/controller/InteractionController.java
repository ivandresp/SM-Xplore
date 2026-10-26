package com.smxplore.recommendations.infrastructure.adapters.in.web.controller;

import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.ports.in.RegisterViewUseCase;
import com.smxplore.recommendations.infrastructure.adapters.in.web.language.RequestLanguageResolver;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.AuthenticatedUser;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interactions")
@Tag(name = "Interactions", description = "Implicit interactions used as recommendation signals.")
public class InteractionController {

    private final RegisterViewUseCase registerViewUseCase;
    private final JwtUserResolver jwtUserResolver;
    private final RequestLanguageResolver languageResolver;

    @PostMapping("/view/{itemType}/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Register that the authenticated tourist viewed a catalog item")
    public Mono<Void> registerView(
            @PathVariable ItemType itemType,
            @PathVariable UUID itemId,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = jwtUserResolver.resolve(jwt);
        String language = languageResolver.resolve(acceptLanguage);
        return registerViewUseCase.handle(user.touristId(), itemType, itemId, language);
    }
}
