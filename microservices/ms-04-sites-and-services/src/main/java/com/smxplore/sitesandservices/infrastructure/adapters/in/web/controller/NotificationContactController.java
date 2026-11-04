package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.application.notification.NotificationContactService;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification.NotificationContactResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification.UpsertNotificationContactRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.NotificationWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notification-contacts")
@Tag(
        name = "Notification contacts",
        description = "Email addresses used to deliver notifications outside the inbox."
)
public class NotificationContactController {

    private final NotificationContactService contactService;
    private final NotificationWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;

    @PutMapping("/{userId}")
    @Operation(summary = "Register or replace the email address of a notification recipient")
    public Mono<NotificationContactResponse> upsert(
            @PathVariable UUID userId,
            @Valid @RequestBody UpsertNotificationContactRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return contactService.upsert(userId, request.email(), actor)
                .map(webMapper::toResponse);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove the email address of a notification recipient")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return contactService.delete(userId, actor)
                .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }
}
