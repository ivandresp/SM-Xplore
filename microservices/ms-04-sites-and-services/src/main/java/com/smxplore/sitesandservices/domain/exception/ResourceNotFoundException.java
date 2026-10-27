package com.smxplore.sitesandservices.domain.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final UUID resourceId;

    public ResourceNotFoundException(String resource, UUID resourceId) {
        super(resource + " not found: " + resourceId);
        this.resource = resource;
        this.resourceId = resourceId;
    }

    public String resource() {
        return resource;
    }

    public UUID resourceId() {
        return resourceId;
    }
}
