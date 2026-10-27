package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;

public record AuditReport(String filename, byte[] content, int rows) {

    public AuditReport {
        if (filename == null || filename.isBlank()) {
            throw new ValidationException("El nombre del reporte es obligatorio");
        }
        if (content == null) {
            throw new ValidationException("El contenido del reporte es obligatorio");
        }
        if (rows < 0) {
            throw new ValidationException("La cantidad de filas no puede ser negativa");
        }
    }
}
