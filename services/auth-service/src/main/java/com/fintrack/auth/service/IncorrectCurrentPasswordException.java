package com.fintrack.auth.service;

/**
 * The current-password check failed during an authenticated change-password
 * request. Distinct from login's InvalidCredentials — the caller is already
 * authenticated, so this is a form-field error (400), not an auth failure.
 */
public class IncorrectCurrentPasswordException extends RuntimeException {

    public IncorrectCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
