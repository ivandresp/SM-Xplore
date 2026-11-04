package com.smxplore.securityreports.domain.model;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.domain.exceptions.InvalidStatusTransitionException;
import com.smxplore.securityreports.domain.model.types.UserRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityReportTests {

    @Test
    void cannotSolveReportBeforeItIsReported() {
        var report = validReport();

        assertThatThrownBy(report::markAsSolved)
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("must be reported");
    }

    @Test
    void followsOngoingReportedSolvedFlow() {
        var report = validReport();

        report.markAsReported();
        report.markAsSolved();

        assertThat(report.getStatus()).isEqualTo(SecurityReportStatus.SOLVED);
    }

    @Test
    void cannotReportAnAlreadyReportedReport() {
        var report = validReport();
        report.markAsReported();

        assertThatThrownBy(report::markAsReported)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void deduplicatesPhotosAndRejectsNonHttpUrls() {
        var report = validReport();

        report.addPhotos(List.of("https://example.com/a.jpg", "https://example.com/a.jpg"));
        assertThat(report.getPhotosUrls()).containsExactly("https://example.com/a.jpg");

        assertThatThrownBy(() -> report.addPhoto("javascript:alert(1)"))
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void limitsTheNumberOfPhotos() {
        var report = validReport();
        for (int index = 0; index < 10; index++) {
            report.addPhoto("https://example.com/" + index + ".jpg");
        }

        assertThatThrownBy(() -> report.addPhoto("https://example.com/extra.jpg"))
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("more than 10");
    }

    private static SecurityReport validReport() {
        return SecurityReport.builder()
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .title("Reporte de prueba")
                .description("Descripción")
                .build();
    }
}
