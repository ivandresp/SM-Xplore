package com.smxplore.sitesandservices.infrastructure.adapters.in.web.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdResolver {

    public String resolve(String correlationId) {
        return isBlank(correlationId)
                ? UUID.randomUUID().toString()
                : correlationId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
