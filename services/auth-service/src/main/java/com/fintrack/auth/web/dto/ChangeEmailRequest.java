package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank(message = "newEmail is required")
        @Email(message = "newEmail must be a valid email address")
        @Size(max = 320, message = "newEmail must be at most 320 characters")
        String newEmail,

        @NotBlank(message = "currentPassword is required")
        String currentPassword
) {
}
