package com.smxplore.users.domain.exception;

public class AnomalyAlreadyResolvedException extends DomainException {

    public AnomalyAlreadyResolvedException() {
        super("La anomalía ya está resuelta");
    }
}
