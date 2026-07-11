package com.fintrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A household groups members (owner, partner, children). Signup auto-creates a
 * single-member household so every row in every service is household-scoped
 * from day one (see ARCHITECTURE.md — retrofitting this is what's expensive).
 */
@Entity
@Table(name = "households", schema = "auth")
public class Household {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    // App-assigned so the value is available in the same transaction's response
    // (a DB-default-only column would be null on the entity until re-read)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Household() {
        // JPA only
    }

    public Household(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}