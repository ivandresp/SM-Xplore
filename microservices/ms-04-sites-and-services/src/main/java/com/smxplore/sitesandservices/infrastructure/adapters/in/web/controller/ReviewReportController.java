package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewReportsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ReportReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ResolveReviewReportUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.CreateReviewReportRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ResolveReviewReportRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewReportResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogRequestMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.CorrelationIdResolver;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
@Tag(name = "Review reports", description = "Reports raised against reviews and their moderation.")
public class ReviewReportController {

    private final ReportReviewUseCase reportUseCase;
    private final ListReviewReportsUseCase listUseCase;
    private final ResolveReviewReportUseCase resolveUseCase;
    private final CatalogRequestMapper requestMapper;
    private final CatalogWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;
    private final CorrelationIdResolver correlationIdResolver;

    @PostMapping("/{reviewId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a review")
    public Mono<ReviewReportResponse> report(
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateReviewReportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return reportUseCase.handle(requestMapper.toDomain(reviewId, request), actor)
                .map(webMapper::toResponse);
    }

    @GetMapping("/reports")
    @Operation(summary = "List review reports (administrators only)")
    public Mono<PageResponse<ReviewReportResponse>> list(
            @RequestParam(required = false) ReviewReportStatus status,
            @RequestParam(required = false) UUID reviewId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        ReviewReportFilter filter = new ReviewReportFilter(status, reviewId, page, size);
        return listUseCase.handle(filter, actor)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(summary = "Accept or dismiss a review report (administrators only)")
    public Mono<ReviewReportResponse> resolve(
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReviewReportRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return resolveUseCase.handle(
                        reportId,
                        request.status(),
                        request.resolution(),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }
}
