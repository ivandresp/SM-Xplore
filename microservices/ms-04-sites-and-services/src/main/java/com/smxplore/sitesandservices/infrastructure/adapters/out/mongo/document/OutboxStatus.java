package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
