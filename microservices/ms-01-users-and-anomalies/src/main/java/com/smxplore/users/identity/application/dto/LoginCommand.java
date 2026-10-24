package com.smxplore.users.identity.application.dto;

public record LoginCommand(String email, String rawPassword) {}
