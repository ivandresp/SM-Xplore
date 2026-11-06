package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AnomalySeverity;

public record ClassifyAnomalyCommand(AnomalySeverity severity) {

    public ClassifyAnomalyCommand {
        if (severity == null) {
            throw new ValidationException("La severidad de la anomalía es obligatoria");
        }
    }
}
