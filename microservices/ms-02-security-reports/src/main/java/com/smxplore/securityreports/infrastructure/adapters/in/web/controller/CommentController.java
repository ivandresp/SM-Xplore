package com.smxplore.securityreports.infrastructure.adapters.in.web.controller;

import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.usecases.CommentReportUseCasePort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportCommentsUseCasePort;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.CommentRequest;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.CommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.smxplore.securityreports.infrastructure.adapters.in.web.mapper.SecurityReportWebMapper;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/security-reports/{reportId}/comments")
@Tag(name = "Security Reports Comments", description = "Comments for Security incidents reported by users")
public class CommentController {

    private final CommentReportUseCasePort commentReportUseCase;
    private final ConsultReportCommentsUseCasePort consultReportCommentsUseCase;
    private final SecurityReportWebMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment a security report (the author is taken from the token)")
    public Mono<CommentResponse> comment(@PathVariable UUID reportId,
                                         @Valid @RequestBody CommentRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        var user = new UserRef(UUID.fromString(Objects.requireNonNull(jwt.getSubject())), jwt.getClaimAsString("name"));
        var comment = mapper.toDomain(request).toBuilder().reportId(reportId).user(user).build();
        return commentReportUseCase.handle(comment).map(mapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List the comments of a security report")
    public Flux<CommentResponse> findComments(@PathVariable UUID reportId) {
        return consultReportCommentsUseCase.handle(reportId).map(mapper::toResponse);
    }
}
