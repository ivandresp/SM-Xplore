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

        assertThatThrownBy(() -> report.markAsSolved(UUID.randomUUID(), "corr-1"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("must be reported");
    }

    @Test
    void followsOngoingReportedSolvedFlow() {
        var report = validReport();
        var actor = UUID.randomUUID();

        report.markAsReported(actor, "corr-1");
        report.markAsSolved(actor, "corr-2");

        assertThat(report.getStatus()).isEqualTo(SecurityReportStatus.SOLVED);
        assertThat(report.getLastStatusChange()).isNotNull();
        assertThat(report.getLastStatusChange().actorId()).isEqualTo(actor);
        assertThat(report.getLastStatusChange().from()).isEqualTo(SecurityReportStatus.REPORTED);
        assertThat(report.getLastStatusChange().to()).isEqualTo(SecurityReportStatus.SOLVED);
        assertThat(report.getLastStatusChange().correlationId()).isEqualTo("corr-2");
    }

    @Test
    void cannotReportAnAlreadyReportedReport() {
        var report = validReport();
        report.markAsReported(UUID.randomUUID(), "corr-1");

        assertThatThrownBy(() -> report.markAsReported(UUID.randomUUID(), "corr-2"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    /**
     * MS02-15: cada transicion deja rastro de quien la hizo, desde/hacia que estado y cuando.
     */
    @Test
    void recordsTheActorAndTimestampOfTheStatusChange() {
        var report = validReport();
        var actor = UUID.randomUUID();

        report.markAsReported(actor, "corr-42");

        var change = report.getLastStatusChange();
        assertThat(change).isNotNull();
        assertThat(change.actorId()).isEqualTo(actor);
        assertThat(change.from()).isEqualTo(SecurityReportStatus.ONGOING);
        assertThat(change.to()).isEqualTo(SecurityReportStatus.REPORTED);
        assertThat(change.correlationId()).isEqualTo("corr-42");
        assertThat(change.at()).isNotNull();
        assertThat(report.getUpdatedAt()).isEqualTo(change.at());
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
