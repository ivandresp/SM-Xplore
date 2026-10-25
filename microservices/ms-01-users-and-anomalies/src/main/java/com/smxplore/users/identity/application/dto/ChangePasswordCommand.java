package com.smxplore.users.identity.application.dto;

public record ChangePasswordCommand(String currentPassword, String newPassword) {}
