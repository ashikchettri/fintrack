package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accept a household invite: prove the code, set a password, and pick a display
 * name. Creates the account (email already proven by the invite) and joins the
 * household. Password policy matches signup (NIST 12–128).
 */
public record AcceptInviteRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 320, message = "email must be at most 320 characters")
        String email,

        @NotBlank(message = "code is required")
        String code,

        @NotBlank(message = "password is required")
        @Size(min = 12, max = 128, message = "password must be between 12 and 128 characters")
        String password,

        @Size(max = 100, message = "name must be at most 100 characters")
        String name
) {
}
