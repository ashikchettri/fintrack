package com.fintrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending invitation for someone to join a household. Same hardening as the
 * verification/reset codes (ADR 004/005): the code is hashed at rest, expires,
 * and dies after too many wrong guesses. Longer TTL than a login code — an
 * invite email may sit unread for a day or two.
 */
@Entity
@Table(name = "household_invites", schema = "auth")
public class HouseholdInvite {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private HouseholdMember invitedBy;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HouseholdRole role;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HouseholdInvite() {
        // JPA only
    }

    public HouseholdInvite(Household household, HouseholdMember invitedBy, String email,
                           HouseholdRole role, String codeHash, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.household = household;
        this.invitedBy = invitedBy;
        this.email = email;
        this.role = role;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public UUID getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public String getEmail() {
        return email;
    }

    public HouseholdRole getRole() {
        return role;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Unusable: already accepted, expired, or too many wrong guesses. */
    public boolean isDead(Instant now, int maxAttempts) {
        return isAccepted() || isExpired(now) || attempts >= maxAttempts;
    }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void accept(Instant now) {
        this.acceptedAt = now;
    }
}
