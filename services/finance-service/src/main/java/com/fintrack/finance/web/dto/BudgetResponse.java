package com.fintrack.finance.web.dto;

import java.util.List;

/**
 * The household budget: its currency and lines (income, expenses, savings).
 * Monthly/annual figures and the summary (leftover, savings rate) are derived
 * in the UI, exactly like the spreadsheet.
 */
public record BudgetResponse(String currency, List<BudgetLineDto> lines) {
}
