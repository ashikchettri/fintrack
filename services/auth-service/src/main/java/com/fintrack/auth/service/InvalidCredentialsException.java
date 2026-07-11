package com.fintrack.auth.service;

/**
 * Wrong email OR wrong password — deliberately indistinguishable, in message
 * and in timing (see LoginService), so login can't be used to enumerate accounts.
 * Mapped to HTTP 401 by the global exception handler.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
