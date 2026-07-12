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
 * A pending password-reset code (ADR 005). Deliberately its own entity, not a
 * "purpose" flag on the verification code: consumption here has heavyweight
 * side effects (session revocation) that must never be reachable from the
 * signup-verification path.
 */
@Entity
@Table(name = "password_reset_codes", schema = "auth")
public class PasswordResetCode {

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

    protected PasswordResetCode() {
        // JPA only
    }

    public PasswordResetCode(User user, String codeHash, Instant createdAt, Instant expiresAt) {
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

    public boolean isDead(Instant now, int maxAttempts) {
        return consumedAt != null || now.isAfter(expiresAt) || attempts >= maxAttempts;
    }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
