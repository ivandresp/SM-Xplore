package com.smxplore.sitesandservices.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notifications.email")
public class NotificationEmailProperties {

    private boolean enabled;
    private String from = "no-reply@smxplore.local";
    private String subjectPrefix = "[SM-Xplore] ";

    @PostConstruct
    public void validate() {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("app.notifications.email.from is required");
        }
        if (!from.contains("@")) {
            throw new IllegalStateException("app.notifications.email.from is not a valid address: " + from);
        }
        if (subjectPrefix == null) {
            subjectPrefix = "";
        }
    }
}
