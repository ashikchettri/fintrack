package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password policy follows NIST SP 800-63B: enforce length (12–128), don't
 * enforce composition rules (special chars etc.) — length beats complexity,
 * and composition rules push users toward predictable patterns.
 */
public record SignupRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 320, message = "email must be at most 320 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 12, max = 128, message = "password must be between 12 and 128 characters")
        String password
) {
}