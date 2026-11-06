package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.application.reviews.ConsultGuideReviewReportsUseCase;
import com.smxplore.activitiesevents.application.reviews.ConsultManagerReviewReportsUseCase;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewReportsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ResolveReviewReportUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.PaginationLimits;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ResolveReviewReportRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewReportResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ReviewReportWebMapper;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/review-reports")
@RequiredArgsConstructor
@Tag(name = "Review reports")
public class ReviewReportController {

    private static final String ADMIN = "ADMIN";

    private final ConsultReviewReportsUseCasePort consultReviewReportsUseCase;
    private final ConsultManagerReviewReportsUseCase consultManagerReviewReportsUseCase;
    private final ConsultGuideReviewReportsUseCase consultGuideReviewReportsUseCase;
    private final ResolveReviewReportUseCasePort resolveReviewReportUseCase;
    private final ReviewReportWebMapper mapper;
    private final JwtProperties jwtProperties;

    @GetMapping
    @Operation(summary = "List review reports by status (admin sees every site, a manager only its own)")
    public Flux<ReviewReportResponse> findByStatus(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "PENDING") ReviewReportStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        var pageSize = PaginationLimits.clamp(limit);
        if (ADMIN.equals(role(jwt))) {
            return consultReviewReportsUseCase.handle(status, pageSize)
                    .map(mapper::toResponse);
        }
        return consultManagerReviewReportsUseCase
                .handle(subject(jwt), jwt.getTokenValue(), status, pageSize)
                .map(mapper::toResponse);
    }

    @GetMapping("/inbox")
    @Operation(summary = "Pending reports about the activities of the authenticated guide")
    public Flux<ReviewReportResponse> inbox(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "PENDING") ReviewReportStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        return consultGuideReviewReportsUseCase
                .handle(subject(jwt), status, PaginationLimits.clamp(limit))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{reportId}")
    @Operation(summary = "Resolve or dismiss a review report (admin or the entity in charge)")
    public Mono<ReviewReportResponse> resolve(@PathVariable UUID reportId,
                                              @Valid @RequestBody ResolveReviewReportRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return resolveReviewReportUseCase.handle(reportId, request.status(), subject(jwt), role(jwt))
                .map(mapper::toResponse);
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
