package com.fintrack.insight.web.dto;

/** A grounded answer to a natural-language spending question (ADR 013). */
public record AnswerResponse(String question, String answer) {
}
