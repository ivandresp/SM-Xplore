package com.smxplore.users.application.dto;

public record UserStatistics(
        long total,
        long active,
        long blocked,
        long suspended,
        long deleted,
        long tourists,
        long guides,
        long providers,
        long approvedProviders,
        long destinationManagers,
        long admins,
        long pendingProviders,
        long newUsersLast30Days) {}
