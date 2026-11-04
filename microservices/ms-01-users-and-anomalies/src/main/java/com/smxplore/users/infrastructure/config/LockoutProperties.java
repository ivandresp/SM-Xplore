package com.smxplore.users.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.lockout")
public record LockoutProperties(boolean enabled, int maxAttempts, Duration window, Duration duration) {}
