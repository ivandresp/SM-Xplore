package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Review;
import java.util.List;

public record ReportedReviewView(ReviewView review, List<ReviewReportView> reports) {

    public static ReportedReviewView from(Review review) {
        return new ReportedReviewView(
                ReviewView.from(review),
                review.reports().stream().map(ReviewReportView::from).toList());
    }
}
