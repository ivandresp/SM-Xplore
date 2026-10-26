package com.smxplore.recommendations.infrastructure.adapters.in.web.controller;

import com.smxplore.recommendations.domain.ports.in.ManagePreferencesUseCase;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.PreferenceRequest;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.PreferenceResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.mapper.RecommendationWebMapper;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.AuthenticatedUser;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/preferences")
@Tag(name = "Preferences", description = "Explicit category preferences for the authenticated tourist.")
public class PreferenceController {

    private final ManagePreferencesUseCase managePreferencesUseCase;
    private final RecommendationWebMapper mapper;
    private final JwtUserResolver jwtUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an explicit like for a category")
    public Mono<PreferenceResponse> addPreference(
            @Valid @RequestBody PreferenceRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = jwtUserResolver.resolve(jwt);
        return managePreferencesUseCase.addPreference(user.touristId(), request.categoryId(), user.language())
                .map(mapper::toPreferenceResponse);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an explicit like for a category")
    public Mono<Void> removePreference(
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser user = jwtUserResolver.resolve(jwt);
        return managePreferencesUseCase.removePreference(user.touristId(), categoryId);
    }
}
