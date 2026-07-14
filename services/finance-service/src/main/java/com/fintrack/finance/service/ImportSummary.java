package com.fintrack.finance.service;

import com.fintrack.finance.service.csv.CsvParseResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What one CSV upload did: how many rows landed, how many were duplicates of a
 * previous import, which accounts were auto-created, and the money totals for
 * the batch. This is the payload the "upload → dashboard" moment renders.
 */
public record ImportSummary(
        UUID importId,
        String fileName,
        String currency,
        int rowsParsed,
        int imported,
        int duplicatesSkipped,
        int failedRows,
        List<String> accountsCreated,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal net,
        List<CsvParseResult.RowError> errors) {
}
