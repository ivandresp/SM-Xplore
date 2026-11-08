package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultReportCountsUseCaseTests {

    private final SecurityReportRepositoryPort securityReportRepository = mock(SecurityReportRepositoryPort.class);
    private final ConsultReportCountsUseCase useCase = new ConsultReportCountsUseCase(securityReportRepository);

    @Test
    void returnsTheTotalsByStatus() {
        var totals = Map.of(
                SecurityReportStatus.ONGOING, 5L,
                SecurityReportStatus.REPORTED, 2L,
                SecurityReportStatus.SOLVED, 9L);
        when(securityReportRepository.countByStatus()).thenReturn(Mono.just(totals));

        assertThat(useCase.handle().block()).isEqualTo(totals);
    }
}
