package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ModerationDecision;

public record ModerateReviewCommand(ModerationDecision decision, String reason) {}
