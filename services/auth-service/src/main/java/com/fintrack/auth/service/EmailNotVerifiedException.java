package com.fintrack.auth.service;

/**
 * Correct credentials but unverified mailbox. Distinct 403 problem type so
 * the UI can route to the verification screen. Only reachable with a valid
 * password, so it is not an enumeration vector.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address has not been verified");
    }
}
