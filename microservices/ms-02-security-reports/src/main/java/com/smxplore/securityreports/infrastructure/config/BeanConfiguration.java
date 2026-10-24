package com.smxplore.securityreports.infrastructure.config;

import com.smxplore.securityreports.application.*;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BeanConfiguration {
    @Bean
    public ReportIncidentUseCasePort reportIncidentUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ReportIncidentUseCase(securityReportRepository);
    }

    @Bean
    public ConsultOneReportUseCasePort consultOneReportUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultOneReportUseCase(securityReportRepository);
    }

    @Bean
    public ConsultReportsUseCasePort consultReportsUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultReportsUseCase(securityReportRepository);
    }

    @Bean
    public ConsultReportsByUserUseCasePort consultReportsByUserUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultReportsByUserUseCase(securityReportRepository);
    }

    @Bean
    public ChangeReportContentUseCasePort changeReportTextUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ChangeReportContentUseCase(securityReportRepository);
    }

    @Bean
    public AddReportPhotosUseCasePort addReportPhotosUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new AddReportPhotosUseCase(securityReportRepository);
    }

    @Bean
    public CloseReportUseCasePort closeReportUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new CloseReportUseCase(securityReportRepository);
    }

    @Bean
    public MarkReportAsSolvedUseCasePort markReportAsSolvedUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new MarkReportAsSolvedUseCase(securityReportRepository);
    }

    @Bean
    public CommentReportUseCasePort commentReportUseCase(CommentRepositoryPort commentRepository) {
        return new CommentReportUseCase(commentRepository);
    }

    @Bean
    public ConsultReportCommentsUseCasePort consultReportCommentsUseCase(CommentRepositoryPort commentRepository) {
        return new ConsultReportCommentUseCase(commentRepository);
    }
}
