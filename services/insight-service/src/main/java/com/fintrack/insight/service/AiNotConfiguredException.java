package com.fintrack.insight.service;

/** Q&A needs the model configured; there's no deterministic fallback (ADR 013). */
public class AiNotConfiguredException extends RuntimeException {
    public AiNotConfiguredException(String message) {
        super(message);
    }
}
