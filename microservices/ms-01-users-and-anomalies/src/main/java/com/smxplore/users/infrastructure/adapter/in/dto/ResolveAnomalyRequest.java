package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.Size;

public record ResolveAnomalyRequest(@Size(max = 1000) String note) {}
