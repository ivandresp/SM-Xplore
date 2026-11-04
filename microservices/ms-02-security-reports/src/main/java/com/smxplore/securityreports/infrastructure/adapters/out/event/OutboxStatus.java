package com.smxplore.securityreports.infrastructure.adapters.out.event;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
