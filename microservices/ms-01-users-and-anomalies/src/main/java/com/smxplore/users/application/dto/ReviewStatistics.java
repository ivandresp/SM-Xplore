package com.smxplore.users.application.dto;

public record ReviewStatistics(
        long total, long visible, long hidden, long pendingAi, long reported, double averageRating) {}
