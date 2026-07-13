package com.fintrack.auth.service;

/**
 * Wrong, expired, consumed, or over-attempted reset code — or an email with
 * no account. One generic message for all (no enumeration). Mapped to 400.
 */
public class InvalidResetCodeException extends RuntimeException {

    public InvalidResetCodeException() {
        super("Invalid or expired reset code");
    }
}
