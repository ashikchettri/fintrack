package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "email is required")
        String email
) {
}
