package com.fintrack.auth.service;

/** Only a household OWNER may invite new members. */
public class NotHouseholdOwnerException extends RuntimeException {
    public NotHouseholdOwnerException() {
        super("Only the household owner can invite members");
    }
}
