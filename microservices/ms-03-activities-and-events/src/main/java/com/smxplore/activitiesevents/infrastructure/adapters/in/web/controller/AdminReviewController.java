package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsForModerationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ModerateReviewUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.PaginationLimits;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ChangeReviewStatusRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.AdminReviewResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ReviewWebMapper;
import com.smxplore.activitiesevents.infrastructure.config.JwtProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin reviews", description = "Administrative moderation of activity and event reviews.")
public class AdminReviewController {

    private final ConsultReviewsForModerationUseCasePort consultReviews;
    private final ModerateReviewUseCasePort moderateReview;
    private final ReviewWebMapper mapper;
    private final JwtProperties jwtProperties;

    @GetMapping("/api/v1/admin/reviews")
    @Operation(summary = "List reviews by status across activities and events")
    public Flux<AdminReviewResponse> findByStatus(
            @RequestParam(defaultValue = "PENDING_REVIEW") ReviewStatus status,
            @RequestParam(defaultValue = "200") int limit) {
        return consultReviews.handle(status, PaginationLimits.clamp(limit))
                .map(mapper::toAdminResponse);
    }

    @PatchMapping("/api/v1/reviews/{reviewId}/status")
    @Operation(summary = "Publish, hide, delete or return a review to the pending queue")
    public Mono<AdminReviewResponse> moderate(@PathVariable UUID reviewId,
                                              @Valid @RequestBody ChangeReviewStatusRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return moderateReview.handle(reviewId, request.status(), subject(jwt), role(jwt))
                .map(mapper::toAdminResponse);
    }

    private String role(Jwt jwt) {
        var role = UserRole.fromClaim(jwt.getClaimAsString(jwtProperties.getRoleClaim()));
        return role == null ? null : role.name();
    }

    private static UUID subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject is required.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject.");
        }
    }
}
