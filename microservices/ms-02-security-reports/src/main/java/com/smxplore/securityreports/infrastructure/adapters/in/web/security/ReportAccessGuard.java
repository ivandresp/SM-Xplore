package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import com.smxplore.securityreports.domain.model.types.UserRole;
import com.smxplore.securityreports.domain.ports.usecases.ConsultOneReportUseCasePort;
import com.smxplore.securityreports.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Guards a security report by ownership: {@code ADMIN} and {@code DESTINATION_MANAGER} see every
 * report, anybody else only the reports it filed.
 *
 * <p>Without it a report was readable by any authenticated user: the UUIDs are not guessable, but
 * {@code /nearby} lists them by radius, so a tourist could read the title, the description, the
 * photos and the reporter of any incident.
 */
@Component
@RequiredArgsConstructor
public class ReportAccessGuard {

    private final ConsultOneReportUseCasePort consultOneReportUseCase;
    private final JwtUserResolver jwtUserResolver;
    private final JwtProperties jwtProperties;

    /** No-op for staff; otherwise the report must belong to the caller. */
    public Mono<Void> requireAuthorOrStaff(Jwt jwt, UUID reportId) {
        if (isStaff(jwt)) {
            return Mono.empty();
        }
        var requesterId = jwtUserResolver.subjectId(jwt);
        return consultOneReportUseCase.handle(reportId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Security report not found.")))
                .doOnNext(report -> {
                    var authorId = report.getUser() == null ? null : report.getUser().id();
                    if (!requesterId.equals(authorId)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Only the author of the report (or staff) can read it.");
                    }
                })
                .then();
    }

    public boolean isStaff(Jwt jwt) {
        var role = UserRole.fromClaimOrNull(jwt == null ? null : jwt.getClaimAsString(
                jwtProperties.getRoleClaim()));
        return UserRole.ADMIN == role || UserRole.DESTINATION_MANAGER == role;
    }
}
