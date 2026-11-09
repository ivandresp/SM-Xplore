package com.smxplore.users.application.port.out;

import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * MS01-22: cuota de reportes de reseñas por turista.
 *
 * <p>El adaptador es responsable de la ventana temporal (por ejemplo 20 reportes cada 24 horas) y de lanzar
 * {@link com.smxplore.users.domain.exception.ReportQuotaExceededException} cuando se supera el límite.
 */
public interface ReviewReportQuota {

    /**
     * Verifica la cuota del turista y, si queda margen, registra el reporte de forma atómica.
     *
     * @param reporterId identificador del turista que reporta
     * @return {@code Mono.empty()} si el reporte se admite; error con la cuota restante si no
     */
    Mono<Void> checkAndRecord(UUID reporterId);
}
