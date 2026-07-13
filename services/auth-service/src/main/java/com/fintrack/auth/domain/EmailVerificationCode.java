package com.fintrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending email-verification code — hashed at rest, short-lived, and dead
 * after too many wrong guesses (compensating controls for the 4-digit
 * keyspace, ADR 004). One row per user; resends replace.
 */
@Entity
@Table(name = "email_verification_codes", schema = "auth")
public class EmailVerificationCode {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailVerificationCode() {
        // JPA only
    }

    public EmailVerificationCode(User user, String codeHash, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isDead(Instant now, int maxAttempts) {
        return isConsumed() || isExpired(now) || attempts >= maxAttempts;
    }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
