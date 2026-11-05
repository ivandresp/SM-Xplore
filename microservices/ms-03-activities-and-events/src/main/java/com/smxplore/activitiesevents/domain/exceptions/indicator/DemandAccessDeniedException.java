package com.smxplore.activitiesevents.domain.exceptions.indicator;

import java.io.Serial;

/** Raised when the caller's role cannot read demand indicators. */
public class DemandAccessDeniedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DemandAccessDeniedException(String message) {
        super(message);
    }
}
