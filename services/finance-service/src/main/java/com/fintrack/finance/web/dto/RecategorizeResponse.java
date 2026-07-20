package com.fintrack.finance.web.dto;

/**
 * Result of re-running the categorizer over a member's transactions (ADR 009):
 * how many were reviewed and how many landed in a new canonical category.
 */
public record RecategorizeResponse(int reviewed, int changed) {
}
