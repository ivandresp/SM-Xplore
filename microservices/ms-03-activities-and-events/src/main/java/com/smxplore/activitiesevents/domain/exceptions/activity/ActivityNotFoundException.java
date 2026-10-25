package com.smxplore.activitiesevents.domain.exceptions.activity;

import java.io.Serial;
import java.util.UUID;

public class ActivityNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ActivityNotFoundException(UUID activityId) {
        super("Activity with id '" + activityId + "' was not found.");
    }
}
