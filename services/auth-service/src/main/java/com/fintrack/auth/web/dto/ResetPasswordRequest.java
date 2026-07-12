package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "code is required")
        @Pattern(regexp = "\\d{4,8}", message = "code must be numeric")
        String code,

        // same NIST-style policy as signup
        @NotBlank(message = "newPassword is required")
        @Size(min = 12, max = 128, message = "newPassword must be between 12 and 128 characters")
        String newPassword
) {
}
