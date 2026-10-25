package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultReportCommentsUseCasePort {
    Mono<Page<Comment>> handle(UUID reportId, PageRequest pageRequest);
}
