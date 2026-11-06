package com.smxplore.sitesandservices.application.notification;

/**
 * Politica de alertas de baja capacidad ya resuelta desde la configuracion, para que la
 * capa de aplicacion no dependa de clases de infraestructura.
 */
public record LowCapacityPolicy(boolean enabled, int thresholdPercent) {
}
