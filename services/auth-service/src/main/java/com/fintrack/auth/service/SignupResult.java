package com.fintrack.auth.service;

import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.User;

/**
 * Everything created by a successful signup. The controller maps this to the
 * response DTO — entities never cross the HTTP boundary (CLAUDE.md convention).
 */
public record SignupResult(User user, Household household, HouseholdMember member) {
}