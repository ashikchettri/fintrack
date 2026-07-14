package com.fintrack.finance.service.csv;

import java.util.List;

/**
 * Outcome of parsing a CSV: the rows we understood, plus per-row errors for the
 * ones we couldn't. We skip bad rows rather than failing the whole upload — a
 * single malformed line shouldn't sink an otherwise-good statement — and report
 * the skips back so the user knows what didn't land.
 */
public record CsvParseResult(List<ParsedTransaction> rows, List<RowError> errors) {

    /** A row that couldn't be parsed, kept so the import can report it. */
    public record RowError(int rowNumber, String message) {
    }
}
