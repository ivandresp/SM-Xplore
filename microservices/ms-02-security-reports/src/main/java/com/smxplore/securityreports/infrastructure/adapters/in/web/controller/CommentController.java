package com.smxplore.securityreports.infrastructure.adapters.in.web.controller;

import com.smxplore.securityreports.domain.ports.usecases.CommentReportUseCasePort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportCommentsUseCasePort;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.CommentRequest;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.CommentResponse;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.PageResponse;
import com.smxplore.securityreports.infrastructure.adapters.in.web.mapper.SecurityReportWebMapper;
import com.smxplore.securityreports.infrastructure.adapters.in.web.pagination.PageResponseMapper;
import com.smxplore.securityreports.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/security-reports/{reportId}/comments")
@Tag(name = "Security Reports Comments", description = "Comments for Security incidents reported by users")
public class CommentController {

    private final CommentReportUseCasePort commentReportUseCase;
    private final ConsultReportCommentsUseCasePort consultReportCommentsUseCase;
    private final SecurityReportWebMapper mapper;
    private final PageResponseMapper pageResponseMapper;
    private final JwtUserResolver jwtUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment a security report (the author is taken from the token)")
    public Mono<CommentResponse> comment(@PathVariable UUID reportId,
                                         @Valid @RequestBody CommentRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        var user = jwtUserResolver.resolve(jwt);
        var comment = mapper.toDomain(request).toBuilder().reportId(reportId).user(user).build();
        return commentReportUseCase.handle(comment).map(mapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List the comments of a security report using cursor-based pagination")
    public Mono<PageResponse<CommentResponse>> findComments(
            @PathVariable UUID reportId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        var pageRequest = pageResponseMapper.toPageRequest(cursor, size);
        return consultReportCommentsUseCase.handle(reportId, pageRequest)
                .map(page -> pageResponseMapper.toResponse(page, mapper::toResponse));
    }
}
