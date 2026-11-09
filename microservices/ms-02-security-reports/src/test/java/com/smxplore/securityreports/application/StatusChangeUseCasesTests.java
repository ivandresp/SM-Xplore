package com.smxplore.securityreports.application;

import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.application.support.TransactionalOutboxWriter;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MS02-15: close y solve deben propagar el actor y el correlation id hasta el reporte persistido.
 */
class StatusChangeUseCasesTests {

    private final SecurityReportRepositoryPort repository = mock(SecurityReportRepositoryPort.class);
    private final TransactionalOutboxWriter outboxWriter = mock(TransactionalOutboxWriter.class);
    private final SecurityReportEventFactory eventFactory = mock(SecurityReportEventFactory.class);
    private final CloseReportUseCase closeUseCase = new CloseReportUseCase(repository, outboxWriter, eventFactory);
    private final MarkReportAsSolvedUseCase solveUseCase =
            new MarkReportAsSolvedUseCase(repository, outboxWriter, eventFactory);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(repository.save(any(SecurityReport.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(Mono.class), any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void closePersistsTheActorThatClosedTheReport() {
        var report = reportOf(SecurityReportStatus.ONGOING);
        var actor = UUID.randomUUID();
        when(repository.findById(report.getId())).thenReturn(Mono.just(report));

        var saved = closeUseCase.handle(report.getId(), actor, "corr-close").block();

        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SecurityReportStatus.REPORTED);
        assertThat(saved.getLastStatusChange()).isNotNull();
        assertThat(saved.getLastStatusChange().actorId()).isEqualTo(actor);
        assertThat(saved.getLastStatusChange().correlationId()).isEqualTo("corr-close");
        assertThat(saved.getLastStatusChange().from()).isEqualTo(SecurityReportStatus.ONGOING);
        verify(repository).save(any(SecurityReport.class));
    }

    @Test
    void solvePersistsTheActorThatSolvedTheReport() {
        var report = reportOf(SecurityReportStatus.REPORTED);
        var actor = UUID.randomUUID();
        when(repository.findById(report.getId())).thenReturn(Mono.just(report));

        var saved = solveUseCase.handle(report.getId(), actor, "corr-solve").block();

        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SecurityReportStatus.SOLVED);
        assertThat(saved.getLastStatusChange()).isNotNull();
        assertThat(saved.getLastStatusChange().actorId()).isEqualTo(actor);
        assertThat(saved.getLastStatusChange().to()).isEqualTo(SecurityReportStatus.SOLVED);
        assertThat(saved.getLastStatusChange().correlationId()).isEqualTo("corr-solve");
    }

    @Test
    void closeFailsWhenTheReportDoesNotExist() {
        var reportId = UUID.randomUUID();
        when(repository.findById(reportId)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> closeUseCase.handle(reportId, UUID.randomUUID(), "corr-close").block())
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void rejectsATransitionWithoutActor() {
        var report = reportOf(SecurityReportStatus.ONGOING);
        when(repository.findById(report.getId())).thenReturn(Mono.just(report));

        assertThatThrownBy(() -> closeUseCase.handle(report.getId(), null, "corr-close").block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    private static SecurityReport reportOf(SecurityReportStatus status) {
        return SecurityReport.builder()
                .id(UUID.randomUUID())
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .title("Reporte de prueba")
                .description("Descripción")
                .status(status)
                .build();
    }
}
