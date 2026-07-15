package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** OWNER invites this email to join the household (as an ADULT member). */
public record CreateInviteRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email
) {
}
