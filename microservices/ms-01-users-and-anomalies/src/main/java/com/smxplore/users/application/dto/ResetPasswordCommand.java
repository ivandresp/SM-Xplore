package com.smxplore.users.application.dto;

public record ResetPasswordCommand(String token, String newPassword) {}
