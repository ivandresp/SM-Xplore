package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.UUID;

/** Minimal user snapshot stored inside a review: id and name only. */
public record ReviewUserDocument(@Field("id") UUID id, String name) {
}
