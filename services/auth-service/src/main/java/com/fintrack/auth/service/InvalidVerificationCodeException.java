package com.fintrack.auth.service;

/**
 * Wrong, expired, consumed, or over-attempted code — or an email with no
 * account. One generic message for all of them (no enumeration, no oracle
 * about code state). Mapped to HTTP 400.
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException() {
        super("Invalid or expired verification code");
    }
}
