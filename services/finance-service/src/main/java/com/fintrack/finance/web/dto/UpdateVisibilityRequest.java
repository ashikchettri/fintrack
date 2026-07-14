package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Flip a transaction's visibility — the "mark as shared commitment" action
 * (ADR 006). Accepts {@code personal}/{@code shared} case-insensitively.
 */
public record UpdateVisibilityRequest(
        @NotBlank(message = "visibility is required")
        @Pattern(regexp = "(?i)personal|shared", message = "visibility must be 'personal' or 'shared'")
        String visibility
) {
    public Visibility toVisibility() {
        return Visibility.fromDb(visibility);
    }
}
