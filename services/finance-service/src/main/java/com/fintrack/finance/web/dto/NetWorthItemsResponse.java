package com.fintrack.finance.web.dto;

import java.util.List;

/** The editable balance sheet — the manual items only (ADR 014). */
public record NetWorthItemsResponse(String currency, List<NetWorthItemDto> items) {
}
