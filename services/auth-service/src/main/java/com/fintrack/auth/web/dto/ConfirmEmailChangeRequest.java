package com.fintrack.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailChangeRequest(
        @NotBlank(message = "code is required")
        @Pattern(regexp = "\\d{4,8}", message = "code must be numeric")
        String code
) {
}
