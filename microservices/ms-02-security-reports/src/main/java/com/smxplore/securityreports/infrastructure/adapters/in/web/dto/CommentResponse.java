package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID reportId,
        UserRefDto user,
        String content,
        Instant createdAt
) {
}
