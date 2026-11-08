package com.smxplore.securityreports.infrastructure.adapters.in.web.controller;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.*;
import com.smxplore.securityreports.domain.ports.usecases.*;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.ChangeReportContentRequest;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.NearbySecurityReportResponse;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.PageResponse;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.PhotosRequest;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.SecurityReportRequest;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.SecurityReportResponse;
import com.smxplore.securityreports.infrastructure.adapters.in.web.mapper.SecurityReportWebMapper;
import com.smxplore.securityreports.infrastructure.adapters.in.web.pagination.PageResponseMapper;
import com.smxplore.securityreports.infrastructure.adapters.in.web.security.CorrelationIdResolver;
import com.smxplore.securityreports.infrastructure.adapters.in.web.security.JwtUserResolver;
import com.smxplore.securityreports.infrastructure.adapters.in.web.security.ReportAccessGuard;
import com.smxplore.securityreports.infrastructure.config.JwtProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/security-reports")
@Tag(name = "Security Reports", description = "Security incidents reported by users.")
public class SecurityReportController {

    private final ReportIncidentUseCasePort reportIncidentUseCase;
    private final ConsultOneReportUseCasePort consultOneReportUseCase;
    private final ConsultReportsUseCasePort consultReportsUseCase;
    private final ConsultReportCountsUseCasePort consultReportCountsUseCase;
    private final ConsultReportsByUserUseCasePort consultReportsByUserUseCase;
    private final ConsultNearbyReportsUseCasePort consultNearbyReportsUseCase;
    private final ChangeReportContentUseCasePort changeReportTextUseCase;
    private final AddReportPhotosUseCasePort addReportPhotosUseCase;
    private final CloseReportUseCasePort closeReportUseCase;
    private final MarkReportAsSolvedUseCasePort markReportAsSolvedUseCase;
    private final SecurityReportWebMapper mapper;
    private final PageResponseMapper pageResponseMapper;
    private final JwtUserResolver jwtUserResolver;
    private final JwtProperties jwtProperties;
    private final ReportAccessGuard reportAccessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a security incident (the reporter is taken from the token)")
    public Mono<SecurityReportResponse> reportIncident(@Valid @RequestBody SecurityReportRequest request,
                                                       @RequestHeader(value = "X-Correlation-Id", required = false)
                                                       String correlationId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        var user = jwtUserResolver.resolve(jwt);
        var securityReport = mapper.toDomain(request).toBuilder().user(user).build();
        return reportIncidentUseCase.handle(user, securityReport, correlationId(correlationId))
                .map(mapper::toResponse);
    }

    @GetMapping("/nearby")
    @Operation(summary = "List anonymized security reports near a coordinate, ordered by distance; "
            + "coordinates are rounded to three decimals and reporter, description and photos are never exposed")
    public Flux<NearbySecurityReportResponse> findNearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(required = false) SecurityReportStatus status,
            @RequestParam(defaultValue = "20") int size) {
        var query = new NearbyReportsQuery(latitude, longitude, radiusKm, status, size);
        return consultNearbyReportsUseCase.handle(query).map(mapper::toNearbyResponse);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get one security report by id (its author or staff)")
    public Mono<SecurityReportResponse> findOne(@PathVariable UUID reportId,
                                                @AuthenticationPrincipal Jwt jwt) {
        return reportAccessGuard.requireAuthorOrStaff(jwt, reportId)
                .then(Mono.defer(() -> consultOneReportUseCase.handle(reportId)))
                .map(mapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List security reports by status using cursor-based pagination")
    public Mono<PageResponse<SecurityReportResponse>> findByStatus(
            @RequestParam SecurityReportStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        var pageRequest = pageResponseMapper.toPageRequest(cursor, size);
        return consultReportsUseCase.handle(status, pageRequest)
                .map(page -> pageResponseMapper.toResponse(page, mapper::toResponse));
    }

    @GetMapping("/counts")
    @Operation(summary = "Count security reports by status across the whole collection")
    public Mono<Map<SecurityReportStatus, Long>> countByStatus() {
        return consultReportCountsUseCase.handle();
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "List security reports reported by a user using cursor-based pagination")
    public Mono<PageResponse<SecurityReportResponse>> findByUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        requireSelfOrStaff(jwt, userId);
        var pageRequest = pageResponseMapper.toPageRequest(cursor, size);
        return consultReportsByUserUseCase.handle(userId, pageRequest)
                .map(page -> pageResponseMapper.toResponse(page, mapper::toResponse));
    }

    @PatchMapping("/{reportId}/content")
    @Operation(summary = "Change the content of a security report (its author or staff)")
    public Mono<SecurityReportResponse> changeText(@PathVariable UUID reportId,
                                                   @Valid @RequestBody ChangeReportContentRequest request,
                                                   @RequestHeader(value = "X-Correlation-Id", required = false)
                                                   String correlationId,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return requireReportAuthorOrStaff(jwt, reportId)
                .then(Mono.defer(() -> changeReportTextUseCase.handle(
                        reportId, request.title(), request.description(), correlationId(correlationId))))
                .map(mapper::toResponse);
    }

    @PostMapping("/{reportId}/photos")
    @Operation(summary = "Add photos to a security report (its author or staff)")
    public Mono<SecurityReportResponse> addPhotos(@PathVariable UUID reportId,
                                                  @Valid @RequestBody PhotosRequest request,
                                                  @RequestHeader(value = "X-Correlation-Id", required = false)
                                                  String correlationId,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return requireReportAuthorOrStaff(jwt, reportId)
                .then(Mono.defer(() -> addReportPhotosUseCase.handle(
                        reportId, request.photosUrls(), correlationId(correlationId))))
                .map(mapper::toResponse);
    }

    @PostMapping("/{reportId}/close")
    @Operation(summary = "Close a security report (marks it as reported)")
    public Mono<SecurityReportResponse> close(@PathVariable UUID reportId,
                                              @RequestHeader(value = "X-Correlation-Id", required = false)
                                              String correlationId) {
        return closeReportUseCase.handle(reportId, correlationId(correlationId)).map(mapper::toResponse);
    }

    @PostMapping("/{reportId}/solve")
    @Operation(summary = "Mark a security report as solved")
    public Mono<SecurityReportResponse> solve(@PathVariable UUID reportId,
                                              @RequestHeader(value = "X-Correlation-Id", required = false)
                                              String correlationId) {
        return markReportAsSolvedUseCase.handle(reportId, correlationId(correlationId)).map(mapper::toResponse);

    }

    /** Acepta solo identificadores acotados; si el cliente envia basura se genera uno nuevo (MS02-08). */
    private static String correlationId(String header) {
        return CorrelationIdResolver.resolve(header);
    }

    private boolean isAdminOrManager(Jwt jwt) {
        var role = UserRole.fromClaimOrNull(jwt == null ? null : jwt.getClaimAsString(
                jwtProperties.getRoleClaim()));
        return UserRole.ADMIN == role || UserRole.DESTINATION_MANAGER == role;
    }

    private void requireSelfOrStaff(Jwt jwt, UUID userId) {
        if (!isAdminOrManager(jwt) && !jwtUserResolver.subjectId(jwt).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the user themselves or staff can list these reports.");
        }
    }

    private Mono<Void> requireReportAuthorOrStaff(Jwt jwt, UUID reportId) {
        if (isAdminOrManager(jwt)) {
            return Mono.empty();
        }
        var requesterId = jwtUserResolver.subjectId(jwt);
        return consultOneReportUseCase.handle(reportId)
                .doOnNext(report -> {
                    var authorId = report.getUser() == null ? null : report.getUser().id();
                    if (!requesterId.equals(authorId)) {
                        // MS02-10: mismo 404 que un reporte inexistente para no revelar su existencia.
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Security report not found.");
                    }
                })
                .then();
    }

}
