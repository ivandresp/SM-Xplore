package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.CommentForbiddenException;
import com.smxplore.securityreports.domain.exceptions.InvalidCommentException;
import com.smxplore.securityreports.domain.exceptions.InvalidStatusTransitionException;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.CommentReportUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Objects;

@RequiredArgsConstructor
public class CommentReportUseCase implements CommentReportUseCasePort {
    private final CommentRepositoryPort commentRepo;
    private final SecurityReportRepositoryPort securityReportRepo;

    /**
     * Solo el autor del reporte o el staff pueden comentarlo, y solo mientras no este resuelto (MS02-02).
     *
     * <p>La autorizacion se valida aqui, despues de cargar el reporte, y no en el controlador: asi cualquier
     * llamada futura al caso de uso hereda la regla y la prueba unitaria no necesita Spring.
     */
    @Override
    public Mono<Comment> handle(Comment comment, boolean requesterIsStaff) {
        return Mono.justOrEmpty(comment)
                .switchIfEmpty(Mono.error(new InvalidCommentException("Comment cannot be null")))
                .doOnNext(Comment::validate)
                .flatMap(validComment -> securityReportRepo.findById(validComment.getReportId())
                        .switchIfEmpty(Mono.error(new ReportNotFoundException(validComment.getReportId())))
                        .flatMap(report -> {
                            var commenterId = validComment.getUser().id();
                            var authorId = report.getUser() == null ? null : report.getUser().id();
                            if (!requesterIsStaff && !Objects.equals(commenterId, authorId)) {
                                throw new CommentForbiddenException();
                            }
                            if (report.isSolved()) {
                                throw new InvalidStatusTransitionException(
                                        "Comments are not allowed on a solved security report.");
                            }
                            return commentRepo.save(validComment);
                        }));
    }
}
