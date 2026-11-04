package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReputationSummaryView;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.GetReputationSummaryUseCase;
import com.smxplore.users.application.port.in.ListReviewsUseCase;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/public/reviews")
public class PublicReviewController {

    private final ListReviewsUseCase listReviews;
    private final GetReputationSummaryUseCase getReputationSummary;

    public PublicReviewController(ListReviewsUseCase listReviews, GetReputationSummaryUseCase getReputationSummary) {
        this.listReviews = listReviews;
        this.getReputationSummary = getReputationSummary;
    }

    @GetMapping
    public Mono<PageResult<ReviewView>> list(
            @RequestParam String targetType,
            @RequestParam UUID targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listReviews.list(parseTargetType(targetType), targetId, page, size);
    }

    @GetMapping("/summary")
    public Mono<ReputationSummaryView> summary(@RequestParam String targetType, @RequestParam UUID targetId) {
        return getReputationSummary.get(parseTargetType(targetType), targetId);
    }

    private static ReviewTargetType parseTargetType(String targetType) {
        try {
            return ReviewTargetType.fromCode(targetType);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El tipo de destino de la reseña no es válido");
        }
    }
}
