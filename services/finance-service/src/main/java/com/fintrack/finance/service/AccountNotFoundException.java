package com.fintrack.finance.service;

import java.util.UUID;

/**
 * The account doesn't exist, or isn't in the caller's household. Both map to
 * the same 404 — a caller must not be able to tell someone else's account id
 * from a nonexistent one.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID id) {
        super("No account " + id);
    }
}
