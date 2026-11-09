package com.smxplore.securityreports.infrastructure.config;

import com.smxplore.securityreports.application.*;
import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.application.support.TransactionalOutboxWriter;
import com.smxplore.securityreports.domain.ports.out.OutboxPort;
import com.smxplore.securityreports.domain.ports.out.PhotoUrlPolicyPort;
import com.smxplore.securityreports.domain.ports.out.TransactionPort;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class BeanConfiguration {

    @Bean
    public SecurityReportEventFactory securityReportEventFactory(OutboxProperties outboxProperties, Clock clock) {
        return new SecurityReportEventFactory(outboxProperties.getProducer(), clock);
    }

    @Bean
    public TransactionalOutboxWriter transactionalOutboxWriter(
            TransactionPort transaction,
            OutboxPort outbox
    ) {
        return new TransactionalOutboxWriter(transaction, outbox);
    }

    @Bean
    public ReportIncidentUseCasePort reportIncidentUseCase(
            SecurityReportRepositoryPort securityReportRepository,
            TransactionalOutboxWriter outboxWriter,
            SecurityReportEventFactory eventFactory,
            PhotoUrlPolicyPort photoUrlPolicy
    ) {
        return new ReportIncidentUseCase(securityReportRepository, outboxWriter, eventFactory, photoUrlPolicy);
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
    public ConsultReportCountsUseCasePort consultReportCountsUseCase(
            SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultReportCountsUseCase(securityReportRepository);
    }

    @Bean
    public ConsultReportsByUserUseCasePort consultReportsByUserUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultReportsByUserUseCase(securityReportRepository);
    }

    @Bean
    public ConsultNearbyReportsUseCasePort consultNearbyReportsUseCase(SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultNearbyReportsUseCase(securityReportRepository);
    }

    @Bean
    public ChangeReportContentUseCasePort changeReportTextUseCase(
            SecurityReportRepositoryPort securityReportRepository,
            TransactionalOutboxWriter outboxWriter,
            SecurityReportEventFactory eventFactory
    ) {
        return new ChangeReportContentUseCase(securityReportRepository, outboxWriter, eventFactory);
    }

    @Bean
    public AddReportPhotosUseCasePort addReportPhotosUseCase(
            SecurityReportRepositoryPort securityReportRepository,
            TransactionalOutboxWriter outboxWriter,
            SecurityReportEventFactory eventFactory,
            PhotoUrlPolicyPort photoUrlPolicy
    ) {
        return new AddReportPhotosUseCase(securityReportRepository, outboxWriter, eventFactory, photoUrlPolicy);
    }

    @Bean
    public CloseReportUseCasePort closeReportUseCase(
            SecurityReportRepositoryPort securityReportRepository,
            TransactionalOutboxWriter outboxWriter,
            SecurityReportEventFactory eventFactory
    ) {
        return new CloseReportUseCase(securityReportRepository, outboxWriter, eventFactory);
    }

    @Bean
    public MarkReportAsSolvedUseCasePort markReportAsSolvedUseCase(
            SecurityReportRepositoryPort securityReportRepository,
            TransactionalOutboxWriter outboxWriter,
            SecurityReportEventFactory eventFactory
    ) {
        return new MarkReportAsSolvedUseCase(securityReportRepository, outboxWriter, eventFactory);
    }

    @Bean
    public CommentReportUseCasePort commentReportUseCase(CommentRepositoryPort commentRepository,
                                                          SecurityReportRepositoryPort securityReportRepository) {
        return new CommentReportUseCase(commentRepository, securityReportRepository);
    }

    @Bean
    public ConsultReportCommentsUseCasePort consultReportCommentsUseCase(CommentRepositoryPort commentRepository,
                                                                          SecurityReportRepositoryPort securityReportRepository) {
        return new ConsultReportCommentUseCase(commentRepository, securityReportRepository);
    }
}
