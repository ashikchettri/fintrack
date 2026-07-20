package com.fintrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the household budget — a planned income, expense, or saving with
 * its frequency and amount (monthly/annual are derived). Household-scoped
 * (jointly held); the budget is replaced wholesale on save, so lines are simple
 * value rows without their own lifecycle. Money is BigDecimal ↔ NUMERIC(19,4).
 */
@Entity
@Table(name = "budget_lines", schema = "finance")
public class BudgetLine {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BudgetSection section;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BudgetFrequency frequency;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BudgetLine() {
        // JPA only
    }

    public BudgetLine(UUID householdId, BudgetSection section, String category, String name,
                      BudgetFrequency frequency, BigDecimal amount, int sortOrder, String currency) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.section = section;
        this.category = category;
        this.name = name;
        this.frequency = frequency;
        this.amount = amount;
        this.sortOrder = sortOrder;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public BudgetSection getSection() {
        return section;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public BudgetFrequency getFrequency() {
        return frequency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getCurrency() {
        return currency;
    }
}
