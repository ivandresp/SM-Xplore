package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

public record SecurityReportRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Valid LocationDto location,
        @Size(max = 10)
        List<@NotBlank @Size(max = 2048)
                @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String> photosUrls
) {
    public SecurityReportRequest {
        photosUrls = photosUrls == null
                ? List.of()
                : photosUrls.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(url -> !url.isBlank())
                        .distinct()
                        .toList();
    }
}
