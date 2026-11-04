package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PhotosRequest(
        @NotEmpty @Size(max = 10)
        List<@NotBlank @Size(max = 2048)
                @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String> photosUrls
) {
}
