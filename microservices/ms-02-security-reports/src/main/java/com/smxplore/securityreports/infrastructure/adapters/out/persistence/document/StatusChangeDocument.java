package com.smxplore.securityreports.infrastructure.adapters.out.persistence.document;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Embedded audit trail of the last status transition (MS02-15). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusChangeDocument {
    private String actorId;
    private SecurityReportStatus from;
    private SecurityReportStatus to;
    private Instant at;
    private String correlationId;
}
