package com.fintrack.insight.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A natural-language spending question (ADR 013). */
public record AskRequest(
        @NotBlank @Size(max = 500) String question) {
}
