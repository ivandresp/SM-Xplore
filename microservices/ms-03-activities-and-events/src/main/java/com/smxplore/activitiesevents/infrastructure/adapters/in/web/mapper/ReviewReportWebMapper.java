package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewReportResponse;
import org.springframework.stereotype.Component;

@Component
public class ReviewReportWebMapper {

    public ReviewReportResponse toResponse(ReviewReport report) {
        return new ReviewReportResponse(report.id(), report.reviewId(), report.reason(),
                report.status(), report.resolvedAt(), report.createdAt());
    }
}
