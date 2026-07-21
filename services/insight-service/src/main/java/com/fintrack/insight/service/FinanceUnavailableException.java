package com.fintrack.insight.service;

/** finance-service could not be reached or returned an error (ADR 012). */
public class FinanceUnavailableException extends RuntimeException {
    public FinanceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
