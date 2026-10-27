package com.smxplore.users.infrastructure.config;

import com.smxplore.users.domain.model.ReviewAiAction;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
        boolean jsonResponseFormat) {

    private static final String DEFAULT_HEADER = "Authorization";
    private static final String DEFAULT_PREFIX = "Bearer";
    private static final String DEFAULT_MODEL = "littlellm";
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";

    public ReviewAiProperties {
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalArgumentException("El filtro de IA está habilitado pero falta LITTLELLM_URL");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(5);
        }
        if (threshold <= 0 || threshold > 1 || Double.isNaN(threshold)) {
            throw new IllegalArgumentException("El umbral del filtro de IA debe estar entre 0 y 1");
        }
        if (Double.isNaN(temperature)) {
            temperature = 0;
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("La temperatura del filtro de IA debe estar entre 0 y 2");
        }
        baseUrl = baseUrl == null ? null : stripTrailingSlash(baseUrl.trim());
        apiKey = apiKey == null ? "" : apiKey.trim();
        apiKeyHeader = apiKeyHeader == null || apiKeyHeader.isBlank() ? DEFAULT_HEADER : apiKeyHeader.trim();
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
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
