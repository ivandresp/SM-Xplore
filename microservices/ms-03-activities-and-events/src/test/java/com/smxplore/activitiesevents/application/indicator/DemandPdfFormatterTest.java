package com.smxplore.activitiesevents.application.indicator;

import com.smxplore.activitiesevents.domain.model.indicator.DemandComparison;
import com.smxplore.activitiesevents.domain.model.indicator.DemandGranularity;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.indicator.DemandSummary;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemandPdfFormatterTest {

    @Test
    void generatesAValidPdfHeader() {
        var empty = DemandSummary.empty();
        var indicators = new DemandIndicators(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"),
                DemandGranularity.DAY,
                "America/Bogota",
                "MANAGER",
                empty,
                new DemandComparison(empty, empty, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                false,
                Instant.parse("2026-11-01T00:00:00Z"));

        var pdf = DemandPdfFormatter.format(indicators);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }
}
