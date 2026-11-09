package com.smxplore.users.application.dto;

import java.time.Duration;

/**
 * Configuración de la verificación de correo (MS01-18) tal como la consume la capa de aplicación.
 *
 * <p>Se define en {@code application} para no acoplar los servicios de aplicación a
 * {@code infrastructure.config}: la capa de infraestructura es la que enlaza las propiedades de Spring y construye
 * este registro.
 */
public record EmailVerificationPolicy(boolean enabled, Duration tokenTtl) {}
