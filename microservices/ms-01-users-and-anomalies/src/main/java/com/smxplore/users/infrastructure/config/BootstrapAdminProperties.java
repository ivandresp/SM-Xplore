package com.smxplore.users.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email, String password, String fullName) {}
