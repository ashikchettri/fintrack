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
 * A member's income. Member-scoped (one row per member per household); the
 * household cash-flow totals across members. Money is BigDecimal ↔ NUMERIC(19,4).
 */
@Entity
@Table(name = "incomes", schema = "finance")
public class Income {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "salary_amount", precision = 19, scale = 4)
    private BigDecimal salaryAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_frequency", length = 16)
    private PayFrequency salaryFrequency;

    // employer super %, e.g. 11.5000
    @Column(name = "super_rate", precision = 6, scale = 4)
    private BigDecimal superRate;

    @Column(name = "bonus_annual", precision = 19, scale = 4)
    private BigDecimal bonusAnnual;

    @Column(name = "other_income_annual", precision = 19, scale = 4)
    private BigDecimal otherIncomeAnnual;

    @Column(name = "other_income_note", length = 200)
    private String otherIncomeNote;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Income() {
        // JPA only
    }

    public Income(UUID householdId, UUID memberId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.memberId = memberId;
        this.currency = "AUD";
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Salary annualized + bonus + other — the spendable annual income (super excluded). */
    public BigDecimal annualIncome() {
        BigDecimal total = BigDecimal.ZERO;
        if (salaryAmount != null && salaryFrequency != null) {
            total = total.add(salaryAmount.multiply(salaryFrequency.annualMultiplier()));
        }
        if (bonusAnnual != null) {
            total = total.add(bonusAnnual);
        }
        if (otherIncomeAnnual != null) {
            total = total.add(otherIncomeAnnual);
        }
        return total;
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

    public BigDecimal getSalaryAmount() {
        return salaryAmount;
    }

    public void setSalaryAmount(BigDecimal salaryAmount) {
        this.salaryAmount = salaryAmount;
    }

    public PayFrequency getSalaryFrequency() {
        return salaryFrequency;
    }

    public void setSalaryFrequency(PayFrequency salaryFrequency) {
        this.salaryFrequency = salaryFrequency;
    }

    public BigDecimal getSuperRate() {
        return superRate;
    }

    public void setSuperRate(BigDecimal superRate) {
        this.superRate = superRate;
    }

    public BigDecimal getBonusAnnual() {
        return bonusAnnual;
    }

    public void setBonusAnnual(BigDecimal bonusAnnual) {
        this.bonusAnnual = bonusAnnual;
    }

    public BigDecimal getOtherIncomeAnnual() {
        return otherIncomeAnnual;
    }

    public void setOtherIncomeAnnual(BigDecimal otherIncomeAnnual) {
        this.otherIncomeAnnual = otherIncomeAnnual;
    }

    public String getOtherIncomeNote() {
        return otherIncomeNote;
    }

    public void setOtherIncomeNote(String otherIncomeNote) {
        this.otherIncomeNote = otherIncomeNote;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
