package com.smxplore.users.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.port.in.BootstrapAdminUseCase;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class BootstrapAdminRunnerTest {

    private final AtomicInteger invocations = new AtomicInteger();

    private final BootstrapAdminRunner runner = new BootstrapAdminRunner(
            countingUseCase(), new BootstrapAdminProperties(false, "admin@smxplore.local", "ChangeMe123!", "Admin"));

    @Test
    void doesNotCreateAnAdminWhenBootstrapIsDisabled() {
        runner.run(null);

        assertThat(invocations).hasValue(0);
    }

    @Test
    void skipsBootstrapWhenTheCredentialsAreBlank() {
        BootstrapAdminRunner enabled = new BootstrapAdminRunner(
                countingUseCase(), new BootstrapAdminProperties(true, "", "ChangeMe123!", "Admin"));

        enabled.run(null);

        assertThat(invocations).hasValue(0);
    }

    @Test
    void bootstrapsTheAdminWhenEnabledAndConfigured() {
        BootstrapAdminRunner enabled = new BootstrapAdminRunner(
                countingUseCase(), new BootstrapAdminProperties(true, "admin@smxplore.local", "Segura123456", "Admin"));

        enabled.run(null);

        assertThat(invocations).hasValue(1);
    }

    private BootstrapAdminUseCase countingUseCase() {
        return command -> {
            assertThat(command.email()).isNotBlank();
            invocations.incrementAndGet();
            return Mono.empty();
        };
    }
}
