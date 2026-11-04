package com.smxplore.users.application.dto;

public record ChangePasswordCommand(String currentPassword, String newPassword) {}
