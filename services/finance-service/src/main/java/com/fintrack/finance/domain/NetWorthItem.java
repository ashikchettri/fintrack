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
 * One asset or liability on the household balance sheet (ADR 014).
 * Household-scoped (jointly held); the net-worth sheet is replaced wholesale on
 * save, so items are plain value rows. Money is BigDecimal ↔ NUMERIC(19,4).
 */
@Entity
@Table(name = "net_worth_items", schema = "finance")
public class NetWorthItem {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NetWorthKind kind;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NetWorthItem() {
        // JPA only
    }

    public NetWorthItem(UUID householdId, NetWorthKind kind, String category, String name,
                        BigDecimal value, int sortOrder, String currency) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.kind = kind;
        this.category = category;
        this.name = name;
        this.value = value;
        this.sortOrder = sortOrder;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public NetWorthKind getKind() {
        return kind;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getCurrency() {
        return currency;
    }
}
