package com.fintrack.finance.web.dto;

import jakarta.validation.Valid;

import java.util.List;

/** Replace-all save of the household balance sheet (ADR 014). */
public record NetWorthRequest(String currency, @Valid List<NetWorthItemDto> items) {
}
