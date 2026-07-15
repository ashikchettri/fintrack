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
 * Join entity between a user and a household, carrying the member's role.
 * Modeled as its own aggregate (not a @ManyToMany) because phase 7 adds
 * per-member state here: invitations, income profile, join date.
 */
@Entity
@Table(name = "household_members", schema = "auth")
public class HouseholdMember {

    @Id
    private UUID id;

    // Lazy: we usually navigate from member → ids for JWT claims, not full graphs
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HouseholdRole role;

    // human label for the household/shared views; null for members created before
    // invites shipped (falls back to the email's local part at the boundary)
    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HouseholdMember() {
        // JPA only
    }

    public HouseholdMember(Household household, User user, HouseholdRole role) {
        this(household, user, role, null);
    }

    public HouseholdMember(Household household, User user, HouseholdRole role, String displayName) {
        this.id = UUID.randomUUID();
        this.household = household;
        this.user = user;
        this.role = role;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public User getUser() {
        return user;
    }

    public HouseholdRole getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}