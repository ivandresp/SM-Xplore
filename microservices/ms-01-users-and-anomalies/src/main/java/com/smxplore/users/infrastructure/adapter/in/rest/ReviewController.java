package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.CreateReviewCommand;
import com.smxplore.users.application.dto.ReportReviewCommand;
import com.smxplore.users.application.dto.RespondReviewCommand;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.CreateReviewUseCase;
import com.smxplore.users.application.port.in.ReportReviewUseCase;
import com.smxplore.users.application.port.in.RespondReviewUseCase;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.ReviewTargetType;
import com.smxplore.users.infrastructure.adapter.in.dto.CreateReviewRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.ReportReviewRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.RespondReviewRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final CreateReviewUseCase createReview;
    private final RespondReviewUseCase respondReview;
    private final ReportReviewUseCase reportReview;

    public ReviewController(
            CreateReviewUseCase createReview, RespondReviewUseCase respondReview, ReportReviewUseCase reportReview) {
        this.createReview = createReview;
        this.respondReview = respondReview;
        this.reportReview = reportReview;
    }

    @PostMapping
    @PreAuthorize("hasRole('TURISTA')")
    public Mono<ResponseEntity<ReviewView>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReviewRequest request) {
        return createReview
                .create(
                        actorId(jwt),
                        new CreateReviewCommand(
                                parseTargetType(request.targetType()),
                                request.targetId(),
                                request.rating(),
                                request.comment()))
                .map(review -> ResponseEntity.created(URI.create("/api/v1/reviews/" + review.id()))
                        .body(review));
    }

    @PostMapping("/{id}/response")
    @PreAuthorize("hasAnyRole('GUIA','PRESTADORA_SERVICIOS')")
    public Mono<ReviewView> respond(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody RespondReviewRequest request) {
        return respondReview.respond(actorId(jwt), id, new RespondReviewCommand(request.text()));
    }

    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody ReportReviewRequest request) {
        return reportReview.report(actorId(jwt), id, new ReportReviewCommand(request.reason()));
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static ReviewTargetType parseTargetType(String targetType) {
        try {
            return ReviewTargetType.fromCode(targetType);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El tipo de destino de la reseña no es válido");
        }
    }
}
