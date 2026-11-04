package com.smxplore.sitesandservices.infrastructure.adapters.out.event;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
