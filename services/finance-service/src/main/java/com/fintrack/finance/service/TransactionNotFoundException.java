package com.fintrack.finance.service;

import java.util.UUID;

/** No transaction with that id belongs to the caller (member-scoped). */
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID id) {
        super("Transaction not found: " + id);
    }
}
