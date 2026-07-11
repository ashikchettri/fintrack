package com.fintrack.auth.domain;

/**
 * Role of a member within a household. Mirrors the CHECK constraint in V1.
 * Carried as a JWT claim so downstream services authorize without a lookup.
 */
public enum HouseholdRole {
    OWNER,
    ADULT,
    CHILD
}