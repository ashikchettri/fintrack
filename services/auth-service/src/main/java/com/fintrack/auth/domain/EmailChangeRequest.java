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
 * A pending email change (authenticated). The target address is proven by a
 * hashed, short-lived, attempt-capped code before the swap — the old address
 * stays active until then. One row per user; a new request replaces it.
 */
@Entity
@Table(name = "email_change_requests", schema = "auth")
public class EmailChangeRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "new_email", nullable = false, length = 320)
    private String newEmail;

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

    protected EmailChangeRequest() {
        // JPA only
    }

    public EmailChangeRequest(User user, String newEmail, String codeHash,
                              Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.newEmail = newEmail;
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

    public String getNewEmail() {
        return newEmail;
    }

    public String getCodeHash() {
        return codeHash;
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
