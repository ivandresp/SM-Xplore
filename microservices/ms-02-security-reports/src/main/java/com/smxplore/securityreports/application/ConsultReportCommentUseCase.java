package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportCommentsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReportCommentUseCase implements ConsultReportCommentsUseCasePort {
    private final CommentRepositoryPort commentRepo;

    @Override
    public Flux<Comment> handle(UUID reportId) {
        return commentRepo.findAllByReportId(reportId);
    }
}
