package com.fintrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One money movement on an account. Always scoped by household + member (values
 * come from the caller's verified JWT, never request input).
 *
 * <p>{@code amount} is SIGNED — spend is negative, income positive — so
 * aggregation is a plain sum. {@code dedupHash} is the row's natural-key digest
 * that makes CSV re-imports idempotent. Built via {@link Builder}: many fields
 * are optional (category, tags, …), so a builder reads better than a wide
 * constructor.
 */
@Entity
@Table(name = "transactions", schema = "finance")
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 60)
    private String category;

    @Column(length = 60)
    private String subcategory;

    // money: BigDecimal ↔ NUMERIC(19,4), signed. Never double.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "original_description", length = 300)
    private String originalDescription;

    @Column(length = 200)
    private String tags;

    @Column(length = 500)
    private String notes;

    @Convert(converter = VisibilityConverter.class)
    @Column(nullable = false, length = 16)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionSource source;

    @Column(name = "import_id")
    private UUID importId;

    @Column(name = "dedup_hash", nullable = false, length = 64, updatable = false)
    private String dedupHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // JPA only
    }

    private Transaction(Builder b) {
        this.id = UUID.randomUUID();
        this.householdId = b.householdId;
        this.memberId = b.memberId;
        this.accountId = b.accountId;
        this.txnDate = b.txnDate;
        this.description = b.description;
        this.category = b.category;
        this.subcategory = b.subcategory;
        this.amount = b.amount;
        this.currency = b.currency;
        this.originalDescription = b.originalDescription;
        this.tags = b.tags;
        this.notes = b.notes;
        this.visibility = b.visibility == null ? Visibility.PERSONAL : b.visibility;
        this.source = b.source == null ? TransactionSource.MANUAL : b.source;
        this.importId = b.importId;
        this.dedupHash = b.dedupHash;
        this.createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getOriginalDescription() {
        return originalDescription;
    }

    public String getTags() {
        return tags;
    }

    public String getNotes() {
        return notes;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    /** Expose to (or hide from) the household — the "mark as shared commitment" action (ADR 006). */
    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public TransactionSource getSource() {
        return source;
    }

    public UUID getImportId() {
        return importId;
    }

    public String getDedupHash() {
        return dedupHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Fluent builder — required fields validated at {@link #build()}. */
    public static final class Builder {
        private UUID householdId;
        private UUID memberId;
        private UUID accountId;
        private LocalDate txnDate;
        private String description;
        private String category;
        private String subcategory;
        private BigDecimal amount;
        private String currency;
        private String originalDescription;
        private String tags;
        private String notes;
        private Visibility visibility;
        private TransactionSource source;
        private UUID importId;
        private String dedupHash;

        public Builder householdId(UUID v) {
            this.householdId = v;
            return this;
        }

        public Builder memberId(UUID v) {
            this.memberId = v;
            return this;
        }

        public Builder accountId(UUID v) {
            this.accountId = v;
            return this;
        }

        public Builder txnDate(LocalDate v) {
            this.txnDate = v;
            return this;
        }

        public Builder description(String v) {
            this.description = v;
            return this;
        }

        public Builder category(String v) {
            this.category = v;
            return this;
        }

        public Builder subcategory(String v) {
            this.subcategory = v;
            return this;
        }

        public Builder amount(BigDecimal v) {
            this.amount = v;
            return this;
        }

        public Builder currency(String v) {
            this.currency = v;
            return this;
        }

        public Builder originalDescription(String v) {
            this.originalDescription = v;
            return this;
        }

        public Builder tags(String v) {
            this.tags = v;
            return this;
        }

        public Builder notes(String v) {
            this.notes = v;
            return this;
        }

        public Builder visibility(Visibility v) {
            this.visibility = v;
            return this;
        }

        public Builder source(TransactionSource v) {
            this.source = v;
            return this;
        }

        public Builder importId(UUID v) {
            this.importId = v;
            return this;
        }

        public Builder dedupHash(String v) {
            this.dedupHash = v;
            return this;
        }

        public Transaction build() {
            return new Transaction(this);
        }
    }
}
