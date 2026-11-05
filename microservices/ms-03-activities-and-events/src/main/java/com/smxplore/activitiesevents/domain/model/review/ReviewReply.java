package com.smxplore.activitiesevents.domain.model.review;

import java.time.Instant;
import java.util.UUID;

/** Response written by the entity in charge of the reviewed event or activity. */
public record ReviewReply(String message, UUID responderId, Instant createdAt) {
}
