package com.smxplore.recommendations.infrastructure.adapters.in.web.controller;

import com.smxplore.recommendations.domain.model.recommendation.RecommendationRequest;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.mapper.RecommendationWebMapper;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.AuthenticatedUser;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recommendations", description = "Personalized recommendations for tourists.")
public class RecommendationController {

    private final GetRecommendationsUseCase getRecommendationsUseCase;
    private final RecommendationWebMapper mapper;
    private final JwtUserResolver jwtUserResolver;

    @GetMapping
    @Operation(summary = "Get recommendations. TOURIST uses the JWT subject; ADMIN may pass touristId.")
    public Mono<RecommendationResponse> getRecommendations(
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID touristId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser requester = jwtUserResolver.resolve(jwt);
        RecommendationRequest request = RecommendationRequest.builder()
                .requesterId(requester.touristId())
                .admin(requester.isAdmin())
                .requestedTouristId(touristId)
                .siteId(siteId)
                .latitude(lat)
                .longitude(lng)
                .limit(limit)
                .build();
        request.validate();
        return getRecommendationsUseCase.handle(request).map(mapper::toResponse);
    }
}
