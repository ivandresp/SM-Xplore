package com.smxplore.recommendations.infrastructure.adapters.in.web.language;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The JWT is not the source of the user's language. The frontend sends the
 * user's choice in the standard Accept-Language header on each request.
 */
@Component
public class RequestLanguageResolver {

    public static final String DEFAULT_LANGUAGE = "es";

    /**
     * The language tag is persisted in {@code TuristaProfile.language}, so an arbitrarily long
     * header must never become a graph property (MS05-09).
     */
    private static final int MAX_LANGUAGE_LENGTH = 35;

    private static final Pattern LANGUAGE_TAG =
            Pattern.compile("^[a-z]{2,3}(-[a-z0-9]{2,8})*$");

    public String resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        for (String part : acceptLanguage.split(",")) {
            String tag = part.split(";", 2)[0].trim();
            if (tag.isEmpty() || "*".equals(tag)) {
                continue;
            }
            String normalized = tag.replace('_', '-').toLowerCase(Locale.ROOT);
            if (normalized.length() <= MAX_LANGUAGE_LENGTH
                    && LANGUAGE_TAG.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return DEFAULT_LANGUAGE;
    }
}
