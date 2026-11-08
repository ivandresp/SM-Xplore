package com.smxplore.users.application.service;

import com.smxplore.users.domain.model.AuditEntry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convierte entradas de auditoría en CSV. Incluye BOM UTF-8 para que Excel abra correctamente los acentos.
 */
final class AuditCsvFormatter {

    private static final String HEADER = "fecha,actor,rol,accion,usuarioAfectado,detalles\n";

    private AuditCsvFormatter() {}

    static String format(List<AuditEntry> entries) {
        StringBuilder csv = new StringBuilder("\uFEFF").append(HEADER);
        for (AuditEntry entry : entries) {
            csv.append(escape(entry.occurredAt().toString()))
                    .append(',')
                    .append(escape(entry.actorEmail()))
                    .append(',')
                    .append(escape(entry.actorRole().code()))
                    .append(',')
                    .append(escape(entry.action().name()))
                    .append(',')
                    .append(escape(
                            entry.targetUserId() == null
                                    ? ""
                                    : entry.targetUserId().toString()))
                    .append(',')
                    .append(escape(details(entry)))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String details(AuditEntry entry) {
        return entry.details().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(detail -> neutralizeFormula(detail.getKey()) + "=" + neutralizeFormula(detail.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String neutralized = neutralizeFormula(value);
        if (neutralized.contains(",")
                || neutralized.contains("\"")
                || neutralized.contains("\n")
                || neutralized.contains("\r")) {
            return "\"" + neutralized.replace("\"", "\"\"") + "\"";
        }
        return neutralized;
    }

    /**
     * MS01-13: una celda que empieza por {@code =}, {@code +}, {@code -}, {@code @}, tabulador o retorno de carro la
     * interpreta Excel (y LibreOffice) como fórmula, lo que permite ejecutar DDE o exfiltrar datos al abrir el CSV. Se
     * antepone un apóstrofo para que la celda sea texto, igual que hace el exportador de ms-03.
     */
    private static String neutralizeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
