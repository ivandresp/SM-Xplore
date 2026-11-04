package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListNotificationsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.MarkNotificationReadUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification.NotificationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.NotificationWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Per-user notification inbox.")
public class NotificationController {

    private final ListNotificationsUseCase listUseCase;
    private final MarkNotificationReadUseCase markReadUseCase;
    private final NotificationWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;

    @GetMapping
    @Operation(summary = "List notifications for the current user")
    public Mono<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        NotificationFilter filter = new NotificationFilter(
                actor.userId(), unreadOnly, page, size);
        return listUseCase.handle(filter, actor)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    public Mono<NotificationResponse> markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return markReadUseCase.handle(notificationId, actor)
                .map(webMapper::toResponse);
    }
}
