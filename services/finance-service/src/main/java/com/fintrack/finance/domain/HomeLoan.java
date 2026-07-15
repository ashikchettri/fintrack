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
 * A household's home-loan profile — jointly held, so scoped by household only
 * (not member). One row per household; edited in place. Feeds the cash-flow +
 * affordability calculations. Money is BigDecimal ↔ NUMERIC(19,4); never float.
 */
@Entity
@Table(name = "home_loans", schema = "finance")
public class HomeLoan {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false, unique = true)
    private UUID householdId;

    @Column(name = "has_home_loan", nullable = false)
    private boolean hasHomeLoan;

    @Column(length = 100)
    private String lender;

    @Column(name = "loan_amount", precision = 19, scale = 4)
    private BigDecimal loanAmount;

    // annual percentage, e.g. 6.2500 = 6.25%
    @Column(name = "interest_rate", precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", length = 16)
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "repayment_amount", precision = 19, scale = 4)
    private BigDecimal repaymentAmount;

    @Column(name = "has_offset", nullable = false)
    private boolean hasOffset;

    @Column(name = "offset_balance", precision = 19, scale = 4)
    private BigDecimal offsetBalance;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private LoanOwnership ownership;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String notes;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HomeLoan() {
        // JPA only
    }

    public HomeLoan(UUID householdId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.currency = "AUD";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public boolean isHasHomeLoan() {
        return hasHomeLoan;
    }

    public void setHasHomeLoan(boolean hasHomeLoan) {
        this.hasHomeLoan = hasHomeLoan;
    }

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(BigDecimal loanAmount) {
        this.loanAmount = loanAmount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public RepaymentFrequency getRepaymentFrequency() {
        return repaymentFrequency;
    }

    public void setRepaymentFrequency(RepaymentFrequency repaymentFrequency) {
        this.repaymentFrequency = repaymentFrequency;
    }

    public BigDecimal getRepaymentAmount() {
        return repaymentAmount;
    }

    public void setRepaymentAmount(BigDecimal repaymentAmount) {
        this.repaymentAmount = repaymentAmount;
    }

    public boolean isHasOffset() {
        return hasOffset;
    }

    public void setHasOffset(boolean hasOffset) {
        this.hasOffset = hasOffset;
    }

    public BigDecimal getOffsetBalance() {
        return offsetBalance;
    }

    public void setOffsetBalance(BigDecimal offsetBalance) {
        this.offsetBalance = offsetBalance;
    }

    public LoanOwnership getOwnership() {
        return ownership;
    }

    public void setOwnership(LoanOwnership ownership) {
        this.ownership = ownership;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Stamp who edited and when — called on every save. */
    public void touch(UUID memberId) {
        this.updatedBy = memberId;
        this.updatedAt = Instant.now();
    }
}
