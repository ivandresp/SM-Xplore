package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.usecases.ConsultOneReportUseCasePort;
import com.smxplore.securityreports.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MS02-10: un reporte ajeno responde 404, igual que uno inexistente, para eliminar el oraculo.
 */
class ReportAccessGuardTests {

    private final ConsultOneReportUseCasePort consultOneReportUseCase =
            mock(ConsultOneReportUseCasePort.class);
    private final JwtProperties jwtProperties = new JwtProperties();
    private final ReportAccessGuard guard = new ReportAccessGuard(
            consultOneReportUseCase, new JwtUserResolver(jwtProperties), jwtProperties);

    @Test
    void returnsNotFoundForAMissingReport() {
        var reportId = UUID.randomUUID();
        when(consultOneReportUseCase.handle(reportId)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> guard.requireAuthorOrStaff(token(UUID.randomUUID(), "TOURIST"), reportId).block())
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Security report not found.");
                });
    }

    @Test
    void returnsNotFoundForAnotherUsersReport() {
        var reportId = UUID.randomUUID();
        var author = UUID.randomUUID();
        when(consultOneReportUseCase.handle(reportId))
                .thenReturn(Mono.just(reportOf(reportId, author)));

        assertThatThrownBy(() -> guard.requireAuthorOrStaff(token(UUID.randomUUID(), "TOURIST"), reportId).block())
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Security report not found.");
                });
    }

    @Test
    void letsTheAuthorReadTheirOwnReport() {
        var reportId = UUID.randomUUID();
        var author = UUID.randomUUID();
        when(consultOneReportUseCase.handle(reportId))
                .thenReturn(Mono.just(reportOf(reportId, author)));

        assertThatCode(() -> guard.requireAuthorOrStaff(token(author, "TOURIST"), reportId).block())
                .doesNotThrowAnyException();
    }

    @Test
    void letsStaffReadAnyReportWithoutConsultingTheReport() {
        assertThatCode(() -> guard
                .requireAuthorOrStaff(token(UUID.randomUUID(), "DESTINATION_MANAGER"), UUID.randomUUID())
                .block())
                .doesNotThrowAnyException();
    }

    private static SecurityReport reportOf(UUID reportId, UUID authorId) {
        return SecurityReport.builder()
                .id(reportId)
                .user(new UserRef(authorId, "Autor"))
                .title("Reporte existente")
                .build();
    }

    private static Jwt token(UUID subject, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("role", role)
                .build();
    }
}
