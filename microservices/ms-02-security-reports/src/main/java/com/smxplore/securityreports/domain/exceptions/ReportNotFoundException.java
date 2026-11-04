package com.smxplore.securityreports.domain.exceptions;

import java.util.UUID;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(UUID reportId) {
        super("Report with id %s not found".formatted(reportId.toString()));
    }
}