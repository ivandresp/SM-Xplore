package com.smxplore.users.application.dto;

public record AnomalyStatistics(
        long total, long open, long resolved, long openCritical, long openHigh, long openMedium, long openLow) {}
