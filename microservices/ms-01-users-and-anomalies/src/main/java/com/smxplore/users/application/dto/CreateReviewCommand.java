package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;

public record CreateReviewCommand(ReviewTargetType targetType, UUID targetId, int rating, String comment) {}
