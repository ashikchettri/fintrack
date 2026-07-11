package com.fintrack.auth.service;

/**
 * Login throttle tripped. Mapped to HTTP 429. Fired per submitted email
 * regardless of account existence — reveals nothing an attacker can use
 * to enumerate accounts.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Too many failed login attempts — try again later");
    }
}
