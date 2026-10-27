package com.smxplore.users.infrastructure.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSecurityValidatorRunner implements ApplicationRunner {

    private final ProductionSecurityValidator validator;

    public ProductionSecurityValidatorRunner(ProductionSecurityValidator validator) {
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        validator.validate();
    }
}
