package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import java.util.UUID;

/** Minimal user snapshot stored inside a review: id and name only. */
public record ReviewUserDocument(UUID id, String name) {
}
