package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportCommentsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReportCommentUseCase implements ConsultReportCommentsUseCasePort {
    private final CommentRepositoryPort commentRepo;
    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<Page<Comment>> handle(UUID reportId, PageRequest pageRequest) {
        return securityReportRepo.findById(reportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(reportId)))
                .flatMap(report -> commentRepo.findAllByReportId(reportId, pageRequest));
    }
}
