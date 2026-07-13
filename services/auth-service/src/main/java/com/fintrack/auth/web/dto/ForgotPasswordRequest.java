package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "email is required")
        String email
) {
}
