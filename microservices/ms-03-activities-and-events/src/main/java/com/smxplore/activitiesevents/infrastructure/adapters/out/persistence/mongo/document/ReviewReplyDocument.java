package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import java.time.Instant;
import java.util.UUID;

public record ReviewReplyDocument(String message, UUID responderId, Instant createdAt) {
}
