package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewReportsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewReportRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReviewReportListService implements ListReviewReportsUseCase {

    private final ReviewReportRepositoryPort reportRepository;
    private final CatalogAccess access;

    @Override
    public Mono<PageResult<ReviewReport>> handle(ReviewReportFilter filter, Actor actor) {
        return access.requireAdmin(actor)
                .flatMap(admin -> reportRepository.findAll(filter));
    }
}
