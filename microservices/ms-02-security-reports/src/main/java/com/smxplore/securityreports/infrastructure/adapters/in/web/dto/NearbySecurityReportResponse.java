package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Anonymized projection of a security report returned by
 * {@code GET /api/v1/security-reports/nearby}.
 *
 * <p>It deliberately excludes the reporter identity, the free-text description and the photo
 * URLs, and exposes the coordinates rounded to three decimals (~110 m). The same projection is
 * returned to every caller (including the report author and staff) so the endpoint cannot be
 * used to harvest personal data of other reporters (MS02-01).</p>
 */
public record NearbySecurityReportResponse(
        UUID id,
        String title,
        SecurityReportStatus status,
        Double latitude,
        Double longitude,
        Instant createdAt,
        Instant updatedAt
) {
}
