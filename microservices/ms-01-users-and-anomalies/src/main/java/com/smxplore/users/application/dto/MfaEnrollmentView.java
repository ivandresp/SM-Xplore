package com.smxplore.users.application.dto;

import java.util.List;

public record MfaEnrollmentView(String secret, String otpauthUri, List<String> recoveryCodes) {}
