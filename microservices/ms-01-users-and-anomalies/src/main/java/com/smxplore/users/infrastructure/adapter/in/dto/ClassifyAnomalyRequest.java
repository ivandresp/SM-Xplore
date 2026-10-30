package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;

public record ClassifyAnomalyRequest(@NotBlank String severity) {}
