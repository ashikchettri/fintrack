package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.NetWorthItem;
import com.fintrack.finance.domain.NetWorthKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One editable balance-sheet row (ADR 014). Value is a non-negative amount; {@code kind} gives it its sign. */
public record NetWorthItemDto(
        @NotNull NetWorthKind kind,
        @Size(max = 60) String category,
        @NotBlank @Size(max = 120) String name,
        @NotNull @PositiveOrZero BigDecimal value) {

    public static NetWorthItemDto from(NetWorthItem item) {
        return new NetWorthItemDto(item.getKind(), item.getCategory(), item.getName(), item.getValue());
    }
}
