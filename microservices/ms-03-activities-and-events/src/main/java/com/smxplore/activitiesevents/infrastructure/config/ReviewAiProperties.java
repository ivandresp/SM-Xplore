package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.domain.model.review.ReviewAiAction;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Mirrors {@code app.reviews.ai-filter} of ms-04 so both services can share the same littleLLM
 * settings. The filter is off by default; when enabled it needs {@code LITTLELLM_URL}.
 */
@ConfigurationProperties("app.reviews.ai-filter")
public record ReviewAiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String apiKeyHeader,
        String apiKeyPrefix,
        String model,
        String chatPath,
        Duration timeout,
        double threshold,
        double temperature,
        ReviewAiAction action,
        boolean failOpen,
        boolean jsonResponseFormat
) {

    private static final String DEFAULT_HEADER = "Authorization";
    private static final String DEFAULT_PREFIX = "Bearer";
    private static final String DEFAULT_MODEL = "littlellm";
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";

    public ReviewAiProperties {
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalArgumentException(
                    "The review AI filter is enabled but LITTLELLM_URL is missing");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(5);
        }
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("review AI threshold must be between 0 and 1");
        }
        if (!Double.isFinite(temperature)) {
            temperature = 0;
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("review AI temperature must be between 0 and 2");
        }
        baseUrl = baseUrl == null ? null : stripTrailingSlash(baseUrl.trim());
        apiKey = apiKey == null ? "" : apiKey.trim();
        apiKeyHeader = apiKeyHeader == null || apiKeyHeader.isBlank()
                ? DEFAULT_HEADER
                : apiKeyHeader.trim();
        apiKeyPrefix = apiKeyPrefix == null ? DEFAULT_PREFIX : apiKeyPrefix.trim();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        chatPath = chatPath == null || chatPath.isBlank() ? DEFAULT_CHAT_PATH : chatPath.trim();
        if (!chatPath.startsWith("/")) {
            chatPath = "/" + chatPath;
        }
        if (action == null) {
            action = ReviewAiAction.PENDING_REVIEW;
        }
    }

    private static String stripTrailingSlash(String value) {
        var result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
