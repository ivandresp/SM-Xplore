package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ReviewReport;
import java.time.Instant;

public record ReviewReportView(String reporterId, String reason, Instant reportedAt) {

    public static ReviewReportView from(ReviewReport report) {
        return new ReviewReportView(report.reporterId().toString(), report.reason(), report.reportedAt());
    }
}
