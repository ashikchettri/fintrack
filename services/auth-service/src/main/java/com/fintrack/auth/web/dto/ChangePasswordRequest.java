package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        // same NIST-style policy as signup/reset
        @NotBlank(message = "newPassword is required")
        @Size(min = 12, max = 128, message = "newPassword must be between 12 and 128 characters")
        String newPassword
) {
}
