package com.smxplore.activitiesevents.domain.model.review;

import java.util.UUID;

/** Minimal user snapshot stored inside a review: id and name only. */
public record ReviewUser(UUID id, String name) {
}
