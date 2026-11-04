package com.smxplore.users.application.dto;

import java.time.Instant;

public record AdminOverviewView(
        Instant generatedAt,
        UserStatistics users,
        ReviewStatistics reviews,
        AnomalyStatistics anomalies,
        SystemAvailability availability) {}
