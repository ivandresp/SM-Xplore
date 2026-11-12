package com.smxplore.users.application.dto;

import java.util.List;

/**
 * Página de la cola de solicitudes.
 *
 * <p>Usa {@code total} (no {@code totalElements}) para compartir la forma que ya consumen ms-03 y ms-04:
 * {@code {content, page, size, total, totalPages}}.
 */
public record RegistrationPage(List<RegistrationSummaryView> content, int page, int size, long total, int totalPages) {

    public RegistrationPage {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static RegistrationPage of(List<RegistrationSummaryView> content, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new RegistrationPage(content, page, size, total, totalPages);
    }
}
