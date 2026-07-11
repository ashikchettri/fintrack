package com.fintrack.auth.service;

/**
 * Thrown when signup is attempted with an email that already has an account.
 * Mapped to HTTP 409 by the global exception handler.
 *
 * Deliberately does NOT echo the email back: the handler's message is generic,
 * and the exception message stays out of logs-to-client paths.
 */
public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("An account with this email already exists");
    }
}