package com.fintrack.auth.service;

/**
 * Unknown, expired, or revoked refresh token — one generic message for all
 * three, so a probing client learns nothing about token state. Mapped to 401.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
