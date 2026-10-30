package com.smxplore.users.domain.exception;

public class AnomalyNotFoundException extends DomainException {

    public AnomalyNotFoundException() {
        super("La anomalía no existe");
    }
}
