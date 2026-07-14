package com.fintrack.finance.service.csv;

/**
 * The uploaded file can't be imported at all — unreadable, empty, or missing the
 * columns we need (a date, a description, and an amount). Distinct from a per-row
 * parse error: this fails the whole request as a 400.
 */
public class CsvImportException extends RuntimeException {
    public CsvImportException(String message) {
        super(message);
    }

    public CsvImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
