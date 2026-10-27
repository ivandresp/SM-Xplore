package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReviewStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewSummaryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.RespondToReviewUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ChangeReviewStatusRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.CreateReviewRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.RespondReviewRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewSummaryResponse;
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
@Tag(name = "Reviews", description = "Site and service reviews stored in MongoDB.")
public class ReviewController {

    private final CreateReviewUseCase createUseCase;
    private final RespondToReviewUseCase respondUseCase;
    private final ChangeReviewStatusUseCase changeStatusUseCase;
    private final GetReviewUseCase getUseCase;
    private final ListReviewsUseCase listUseCase;
    private final GetReviewSummaryUseCase summaryUseCase;
    private final CatalogRequestMapper requestMapper;
    private final CatalogWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;
    private final CorrelationIdResolver correlationIdResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a review for a site or service")
    public Mono<ReviewResponse> create(
            @Valid @RequestBody CreateReviewRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return createUseCase.handle(
                        requestMapper.toDomain(request),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @PostMapping("/{reviewId}/response")
    @Operation(summary = "Respond to a review as the site or service owner")
    public Mono<ReviewResponse> respond(
            @PathVariable UUID reviewId,
            @Valid @RequestBody RespondReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return respondUseCase.handle(
                        requestMapper.toReviewResponse(reviewId, request),
                        actor)
                .map(webMapper::toResponse);
    }

    @PatchMapping("/{reviewId}/status")
    @Operation(summary = "Moderate a review")
    public Mono<ReviewResponse> changeStatus(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ChangeReviewStatusRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return changeStatusUseCase.handle(
                        requestMapper.toReviewStatus(reviewId, request),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get the rating summary for a site or service")
    public Mono<ReviewSummaryResponse> summary(
            @RequestParam ItemType itemType,
            @RequestParam UUID itemId
    ) {
        return summaryUseCase.handle(itemType, itemId).map(webMapper::toResponse);
    }

    @GetMapping("/{reviewId}")
    @Operation(summary = "Get a review")
    public Mono<ReviewResponse> get(@PathVariable UUID reviewId) {
        return getUseCase.handle(reviewId).map(webMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List reviews")
    public Mono<PageResponse<ReviewResponse>> list(
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID touristId,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ReviewStatus effectiveStatus = status == null ? ReviewStatus.PUBLISHED : status;
        ReviewFilter filter = new ReviewFilter(itemType, itemId, touristId, effectiveStatus, page, size);
        return listUseCase.handle(filter)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }
}
