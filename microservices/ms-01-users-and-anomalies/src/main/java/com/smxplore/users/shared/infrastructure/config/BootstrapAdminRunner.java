package com.smxplore.users.shared.infrastructure.config;

import com.smxplore.users.identity.application.dto.BootstrapAdminCommand;
import com.smxplore.users.identity.application.port.in.BootstrapAdminUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminUseCase bootstrapAdminUseCase;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminRunner(BootstrapAdminUseCase bootstrapAdminUseCase, BootstrapAdminProperties properties) {
        this.bootstrapAdminUseCase = bootstrapAdminUseCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            LOGGER.warn("Bootstrap de administrador omitido: email o contraseña no configurados");
            return;
        }

        try {
            bootstrapAdminUseCase.bootstrap(
                    new BootstrapAdminCommand(properties.fullName(), properties.email(), properties.password()));
            LOGGER.info("Verificación de administrador inicial completada");
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo crear el administrador inicial", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
