package com.fintrack.auth.service;

/** The invite code is wrong, expired, already used, or over the attempt cap. */
public class InvalidInviteException extends RuntimeException {
    public InvalidInviteException() {
        super("Invalid or expired invitation code");
    }
}
