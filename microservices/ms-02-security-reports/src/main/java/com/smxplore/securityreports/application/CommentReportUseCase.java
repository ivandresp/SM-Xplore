package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.InvalidCommentException;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.CommentReportUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CommentReportUseCase implements CommentReportUseCasePort {
    private final CommentRepositoryPort commentRepo;
    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<Comment> handle(Comment comment) {
        return Mono.justOrEmpty(comment)
                .switchIfEmpty(Mono.error(new InvalidCommentException("Comment cannot be null")))
                .doOnNext(Comment::validate)
                .flatMap(validComment -> securityReportRepo.findById(validComment.getReportId())
                        .switchIfEmpty(Mono.error(new ReportNotFoundException(validComment.getReportId())))
                        .flatMap(report -> commentRepo.save(validComment)));
    }
}
