package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String type,
        String currency,
        BigDecimal openingBalance,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType().name(),
                account.getCurrency(),
                account.getOpeningBalance(),
                account.getCreatedAt());
    }
}
