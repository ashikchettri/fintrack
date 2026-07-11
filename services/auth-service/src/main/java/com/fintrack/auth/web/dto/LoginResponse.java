package com.fintrack.auth.web.dto;

import com.fintrack.auth.service.LoginResult;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken
) {
    public static LoginResponse from(LoginResult result, long expiresInSeconds) {
        return new LoginResponse(result.accessToken(), "Bearer", expiresInSeconds, result.refreshToken());
    }
}
