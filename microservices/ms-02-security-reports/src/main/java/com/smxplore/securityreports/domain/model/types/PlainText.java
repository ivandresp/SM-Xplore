package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;

import java.util.regex.Pattern;

/**
 * Contenido de texto plano para titulos, descripciones y comentarios (MS02-06).
 *
 * <p>La API devuelve JSON, pero un cliente que renderice el contenido como HTML convertiria un titulo en XSS
 * almacenado: el servicio no escapa al guardar (eso duplicaria entidades en los clientes correctos) sino que
 * rechaza etiquetas HTML y caracteres de control. Las etiquetas se detectan con un patron generico de apertura,
 * cierre o comentario, no con una lista de etiquetas "peligrosas" que siempre queda incompleta.
 */
public final class PlainText {

    private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z!][^<>]*>");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private PlainText() {
    }

    /**
     * @throws InvalidReportRequestException si el valor contiene caracteres de control o etiquetas HTML.
     */
    public static void ensurePlainText(String value, String fieldName) {
        if (value == null) {
            return;
        }
        if (CONTROL_CHARACTERS.matcher(value).find()) {
            throw new InvalidReportRequestException(fieldName + " contains control characters.");
        }
        if (HTML_TAG.matcher(value).find()) {
            throw new InvalidReportRequestException(fieldName + " must be plain text: HTML is not allowed.");
        }
    }
}
