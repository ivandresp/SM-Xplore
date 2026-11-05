package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.ports.in.ListAiFlaggedReviewsUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.AdminReviewResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reviews")
@Tag(name = "Admin reviews", description = "Administrative review moderation.")
public class AdminReviewController {

    private final ListAiFlaggedReviewsUseCase listAiFlaggedReviews;
    private final CatalogWebMapper webMapper;

    @GetMapping("/ai-flagged")
    @Operation(summary = "List reviews retained by the automatic AI filter")
    public Mono<PageResponse<AdminReviewResponse>> aiFlagged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return listAiFlaggedReviews.handle(page, size)
                .map(result -> PageResponse.from(result, webMapper::toAdminResponse));
    }
}
