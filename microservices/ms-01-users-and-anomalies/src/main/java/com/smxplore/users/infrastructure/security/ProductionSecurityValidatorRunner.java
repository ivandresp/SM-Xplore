package com.smxplore.users.infrastructure.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el validador de configuración en todos los perfiles. Solo dev, local y test pueden arrancar con valores de
 * desarrollo; el resto falla el arranque ante una configuración insegura.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSecurityValidatorRunner implements ApplicationRunner {

    private static final Profiles LOCAL_PROFILES = Profiles.of("dev", "local", "test");

    private final ProductionSecurityValidator validator;
    private final Environment environment;

    public ProductionSecurityValidatorRunner(ProductionSecurityValidator validator, Environment environment) {
        this.validator = validator;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validator.validate(environment.acceptsProfiles(LOCAL_PROFILES));
    }
}
