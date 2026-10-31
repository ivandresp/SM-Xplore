package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentReportUseCaseTests {

    private final CommentRepositoryPort commentRepository = mock(CommentRepositoryPort.class);
    private final SecurityReportRepositoryPort securityReportRepository = mock(SecurityReportRepositoryPort.class);
    private final CommentReportUseCase useCase = new CommentReportUseCase(commentRepository, securityReportRepository);

    @Test
    void rejectsCommentWhenReportDoesNotExist() {
        var comment = validComment();
        when(securityReportRepository.findById(comment.getReportId())).thenReturn(Mono.empty());

        assertThatThrownBy(() -> useCase.handle(comment).block())
                .isInstanceOf(ReportNotFoundException.class);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void savesCommentWhenReportExists() {
        var comment = validComment();
        var report = SecurityReport.builder()
                .id(comment.getReportId())
                .user(comment.getUser())
                .title("Reporte existente")
                .build();
        when(securityReportRepository.findById(comment.getReportId())).thenReturn(Mono.just(report));
        when(commentRepository.save(comment)).thenReturn(Mono.just(comment));

        assertThat(useCase.handle(comment).block()).isSameAs(comment);

        verify(commentRepository).save(comment);
    }

    private static Comment validComment() {
        return Comment.builder()
                .reportId(UUID.randomUUID())
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .content("Comentario de prueba.")
                .build();
    }
}
