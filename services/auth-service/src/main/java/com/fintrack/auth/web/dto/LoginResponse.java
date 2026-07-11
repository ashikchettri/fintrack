package com.fintrack.auth.web.dto;

import com.fintrack.auth.service.LoginResult;

/**
 * The refresh token is deliberately absent: it travels only as an httpOnly
 * cookie (ADR 003). If it were in the body too, XSS could still steal it.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static LoginResponse from(LoginResult result, long expiresInSeconds) {
        return new LoginResponse(result.accessToken(), "Bearer", expiresInSeconds);
    }
}
