package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.Comment;
import reactor.core.publisher.Mono;

public interface CommentReportUseCasePort {

    /**
     * @param comment         comentario con autor tomado del token.
     * @param requesterIsStaff {@code true} para ADMIN y DESTINATION_MANAGER, que pueden comentar reportes ajenos.
     */
    Mono<Comment> handle(Comment comment, boolean requesterIsStaff);
}
