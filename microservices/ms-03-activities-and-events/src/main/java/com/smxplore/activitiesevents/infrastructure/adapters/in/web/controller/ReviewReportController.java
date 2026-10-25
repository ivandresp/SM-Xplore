package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewReportsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ResolveReviewReportUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ResolveReviewReportRequest;
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

    private final ConsultReviewReportsUseCasePort consultReviewReportsUseCase;
    private final ResolveReviewReportUseCasePort resolveReviewReportUseCase;

    @GetMapping
    @Operation(summary = "List review reports by status (staff)")
    public Flux<ReviewReport> findByStatus(@RequestParam(defaultValue = "PENDING") ReviewReportStatus status) {
        return consultReviewReportsUseCase.handle(status);
    }

    @PatchMapping("/{reportId}")
    @Operation(summary = "Resolve or dismiss a review report (staff)")
    public Mono<ReviewReport> resolve(@PathVariable UUID reportId,
                                      @Valid @RequestBody ResolveReviewReportRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return resolveReviewReportUseCase.handle(reportId, request.status(), subject(jwt));
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
