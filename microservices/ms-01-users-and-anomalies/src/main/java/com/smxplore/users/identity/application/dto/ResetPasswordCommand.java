package com.smxplore.users.identity.application.dto;

public record ResetPasswordCommand(String token, String newPassword) {}
