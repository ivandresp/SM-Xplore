package com.smxplore.recommendations.infrastructure.adapters.in.web.controller;

import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidLocationException;
import com.smxplore.recommendations.domain.model.GeoLocation;
import com.smxplore.recommendations.domain.model.RecommendationQuery;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.mapper.RecommendationWebMapper;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.AuthenticatedUser;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
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
            @RequestParam(required = false)
            @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false)
            @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) UUID touristId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser requester = jwtUserResolver.resolve(jwt);
        UUID effectiveTouristId = effectiveTouristId(requester, touristId);

        if ((lat == null) != (lng == null)) {
            throw new InvalidLocationException("lat and lng must be provided together");
        }
        GeoLocation location = lat == null ? null : new GeoLocation(lat, lng);

        RecommendationQuery query = new RecommendationQuery(effectiveTouristId, siteId, location, limit);
        return getRecommendationsUseCase.handle(query).map(mapper::toResponse);
    }

    private UUID effectiveTouristId(AuthenticatedUser requester, UUID requestedTouristId) {
        if (requestedTouristId == null) {
            return requester.touristId();
        }
        if (!requester.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN can query recommendations for another tourist.");
        }
        return requestedTouristId;
    }
}
