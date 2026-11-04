package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Review;

public record AiFlaggedReviewView(ReviewView review, ReviewAiAssessmentView ai) {

    public static AiFlaggedReviewView from(Review review) {
        return new AiFlaggedReviewView(ReviewView.from(review), ReviewAiAssessmentView.from(review.aiAssessment()));
    }
}
