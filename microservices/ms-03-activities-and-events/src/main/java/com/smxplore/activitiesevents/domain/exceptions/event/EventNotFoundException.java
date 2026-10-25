package com.smxplore.activitiesevents.domain.exceptions.event;

import java.io.Serial;
import java.util.UUID;

public class EventNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EventNotFoundException(UUID eventId) {
        super("Event with id '" + eventId + "' was not found.");
    }
}
