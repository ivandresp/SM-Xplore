package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultReportCommentUseCaseTests {

    private final CommentRepositoryPort commentRepository = mock(CommentRepositoryPort.class);
    private final SecurityReportRepositoryPort securityReportRepository = mock(SecurityReportRepositoryPort.class);
    private final ConsultReportCommentUseCase useCase =
            new ConsultReportCommentUseCase(commentRepository, securityReportRepository);

    @Test
    void rejectsListingCommentsOfMissingReport() {
        var reportId = UUID.randomUUID();
        when(securityReportRepository.findById(reportId)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> useCase.handle(reportId, PageRequest.first(10)).block())
                .isInstanceOf(ReportNotFoundException.class);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void listsCommentsWhenReportExists() {
        var reportId = UUID.randomUUID();
        var report = SecurityReport.builder()
                .id(reportId)
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .title("Reporte")
                .build();
        var page = new Page<Comment>(java.util.List.of(), null);
        when(securityReportRepository.findById(reportId)).thenReturn(Mono.just(report));
        when(commentRepository.findAllByReportId(reportId, PageRequest.first(10))).thenReturn(Mono.just(page));

        assertThat(useCase.handle(reportId, PageRequest.first(10)).block()).isSameAs(page);
    }
}
