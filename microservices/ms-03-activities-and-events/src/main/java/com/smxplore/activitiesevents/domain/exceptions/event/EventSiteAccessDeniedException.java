package com.smxplore.activitiesevents.domain.exceptions.event;

import java.io.Serial;

public class EventSiteAccessDeniedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EventSiteAccessDeniedException(String message) {
        super(message);
    }
}
