package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.ModerateReviewCommand;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReportedReviewView;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.ListReportedReviewsUseCase;
import com.smxplore.users.application.port.in.ModerateReviewUseCase;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.ModerationDecision;
import com.smxplore.users.infrastructure.adapter.in.dto.ModerateReviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ListReportedReviewsUseCase listReportedReviews;
    private final ModerateReviewUseCase moderateReview;

    public AdminReviewController(ListReportedReviewsUseCase listReportedReviews, ModerateReviewUseCase moderateReview) {
        this.listReportedReviews = listReportedReviews;
        this.moderateReview = moderateReview;
    }

    @GetMapping("/reported")
    public Mono<PageResult<ReportedReviewView>> reported(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return listReportedReviews.list(page, size);
    }

    @PatchMapping("/{id}/moderation")
    public Mono<ReviewView> moderate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody ModerateReviewRequest request) {
        return moderateReview.moderate(
                actorId(jwt), id, new ModerateReviewCommand(parseDecision(request.decision()), request.reason()));
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static ModerationDecision parseDecision(String decision) {
        try {
            return ModerationDecision.valueOf(decision);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("La decisión de moderación debe ser HIDE o KEEP");
        }
    }
}
