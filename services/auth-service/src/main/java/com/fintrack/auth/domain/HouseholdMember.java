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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HouseholdMember() {
        // JPA only
    }

    public HouseholdMember(Household household, User user, HouseholdRole role) {
        this.id = UUID.randomUUID();
        this.household = household;
        this.user = user;
        this.role = role;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}