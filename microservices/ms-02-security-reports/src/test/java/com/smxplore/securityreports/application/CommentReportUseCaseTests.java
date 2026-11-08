package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.CommentForbiddenException;
import com.smxplore.securityreports.domain.exceptions.InvalidCommentException;
import com.smxplore.securityreports.domain.exceptions.InvalidStatusTransitionException;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
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

/**
 * MS02-02: solo el autor del reporte o el staff pueden comentarlo, y solo mientras no este resuelto.
 */
class CommentReportUseCaseTests {

    private final CommentRepositoryPort commentRepository = mock(CommentRepositoryPort.class);
    private final SecurityReportRepositoryPort securityReportRepository = mock(SecurityReportRepositoryPort.class);
    private final CommentReportUseCase useCase = new CommentReportUseCase(commentRepository, securityReportRepository);

    @Test
    void rejectsCommentWhenReportDoesNotExist() {
        var comment = validComment();
        when(securityReportRepository.findById(comment.getReportId())).thenReturn(Mono.empty());

        assertThatThrownBy(() -> useCase.handle(comment, false).block())
                .isInstanceOf(ReportNotFoundException.class);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void savesCommentWhenTheAuthorCommentsTheirOwnReport() {
        var comment = validComment();
        when(securityReportRepository.findById(comment.getReportId()))
                .thenReturn(Mono.just(reportOf(comment.getReportId(), comment.getUser())));
        when(commentRepository.save(comment)).thenReturn(Mono.just(comment));

        assertThat(useCase.handle(comment, false).block()).isSameAs(comment);

        verify(commentRepository).save(comment);
    }

    @Test
    void rejectsCommentFromAnotherNonStaffUser() {
        var comment = validComment();
        var anotherAuthor = new UserRef(UUID.randomUUID(), "Otra persona");
        when(securityReportRepository.findById(comment.getReportId()))
                .thenReturn(Mono.just(reportOf(comment.getReportId(), anotherAuthor)));

        assertThatThrownBy(() -> useCase.handle(comment, false).block())
                .isInstanceOf(CommentForbiddenException.class);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void letsStaffCommentAnyReport() {
        var comment = validComment();
        var anotherAuthor = new UserRef(UUID.randomUUID(), "Otra persona");
        when(securityReportRepository.findById(comment.getReportId()))
                .thenReturn(Mono.just(reportOf(comment.getReportId(), anotherAuthor)));
        when(commentRepository.save(comment)).thenReturn(Mono.just(comment));

        assertThat(useCase.handle(comment, true).block()).isSameAs(comment);

        verify(commentRepository).save(comment);
    }

    @Test
    void rejectsCommentsOnASolvedReport() {
        var comment = validComment();
        var solved = reportOf(comment.getReportId(), comment.getUser()).toBuilder()
                .status(SecurityReportStatus.SOLVED)
                .build();
        when(securityReportRepository.findById(comment.getReportId())).thenReturn(Mono.just(solved));

        assertThatThrownBy(() -> useCase.handle(comment, false).block())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("solved");

        verifyNoInteractions(commentRepository);
    }

    @Test
    void rejectsANullComment() {
        assertThatThrownBy(() -> useCase.handle(null, false).block())
                .isInstanceOf(InvalidCommentException.class);
    }

    private static SecurityReport reportOf(UUID id, UserRef author) {
        return SecurityReport.builder()
                .id(id)
                .user(author)
                .title("Reporte existente")
                .build();
    }

    private static Comment validComment() {
        return Comment.builder()
                .reportId(UUID.randomUUID())
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .content("Comentario de prueba.")
                .build();
    }
}
