package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ReportIncidentUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReportIncidentUseCase implements ReportIncidentUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<SecurityReport> handle(UserRef user, SecurityReport report) {
        return Mono.justOrEmpty(report)
                .switchIfEmpty(Mono.error(new InvalidReportRequestException("SecurityReport cannot be null")))
                .map(securityReport -> validateAndBuild(securityReport, user))
                .flatMap(securityReportRepo::save);
    }

    private SecurityReport validateAndBuild(SecurityReport report, UserRef user) {
        if (user == null) throw new InvalidUserReporterException("User cannot be null");
        var reportWithUser = report.toBuilder().user(user).build();
        reportWithUser.validate();
        return reportWithUser;
    }
}
