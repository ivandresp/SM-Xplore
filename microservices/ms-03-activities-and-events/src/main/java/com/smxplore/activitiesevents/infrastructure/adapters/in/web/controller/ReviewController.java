package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsByItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.CreateReviewUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.RespondToReviewUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.CreateReviewRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.RespondReviewRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final CreateReviewUseCasePort createReviewUseCase;
    private final ConsultReviewsByItemUseCasePort consultReviewsByItemUseCase;
    private final RespondToReviewUseCasePort respondToReviewUseCase;
    private final ReviewWebMapper mapper;
    private final JwtProperties jwtProperties;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a review for an event or activity. No reservation is required.")
    public Mono<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return createReviewUseCase.handle(request.itemId(), request.itemType(), subject(jwt),
                        name(jwt), request.rating(), request.comment())
                .map(mapper::toResponse);
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "List the reviews of an event")
    public Flux<ReviewResponse> findByEvent(@PathVariable UUID eventId) {
        return consultReviewsByItemUseCase.handle(eventId,
                        com.smxplore.activitiesevents.domain.model.review.ReviewItemType.EVENT)
                .map(mapper::toResponse);
    }

    @GetMapping("/activities/{activityId}")
    @Operation(summary = "List the reviews of an activity")
    public Flux<ReviewResponse> findByActivity(@PathVariable UUID activityId) {
        return consultReviewsByItemUseCase.handle(activityId,
                        com.smxplore.activitiesevents.domain.model.review.ReviewItemType.ACTIVITY)
                .map(mapper::toResponse);
    }

    @PostMapping("/{reviewId}/response")
    @Operation(summary = "Answer a review. Only the entity in charge of the event/activity or staff.")
    public Mono<ReviewResponse> respond(@PathVariable UUID reviewId,
                                        @Valid @RequestBody RespondReviewRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return respondToReviewUseCase.handle(reviewId, subject(jwt), role(jwt), request.message())
                .map(mapper::toResponse);
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

    private String name(Jwt jwt) {
        var claim = jwt.getClaimAsString(jwtProperties.getNameClaim());
        return claim == null || claim.isBlank() ? jwt.getSubject() : claim;
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString(jwtProperties.getRoleClaim());
    }
}
