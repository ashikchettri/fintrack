package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * No format/length rules here (unlike signup): validation feedback on a login
 * form is an enumeration aid. Anything non-blank gets one Argon2-priced 401.
 */
public record LoginRequest(
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
}
