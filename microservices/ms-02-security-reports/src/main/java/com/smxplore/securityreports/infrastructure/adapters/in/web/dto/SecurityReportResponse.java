package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecurityReportResponse(
        UUID id,
        UserRefDto user,
        String text,
        LocationDto location,
        List<String> photosUrls,
        SecurityReportStatus status,
        Instant createdAt
) {
}
