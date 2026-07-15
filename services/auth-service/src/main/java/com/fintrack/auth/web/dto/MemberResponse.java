package com.fintrack.auth.web.dto;

import java.util.UUID;

/**
 * A household member for the roster (names for the shared-commitments view).
 * {@code name} is the display name, or the email's local part as a fallback.
 */
public record MemberResponse(UUID memberId, String name, String role, boolean isYou) {
}
