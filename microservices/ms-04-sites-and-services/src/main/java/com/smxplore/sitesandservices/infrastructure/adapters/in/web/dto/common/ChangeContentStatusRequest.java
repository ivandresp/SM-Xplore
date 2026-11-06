package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common;

import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeContentStatusRequest(
        @NotNull ContentStatus status
) {
}
