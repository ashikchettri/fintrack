package com.fintrack.finance.service.ai;

/**
 * Thin port over the Anthropic Messages API (ADR 009). Keeps the model call at
 * arm's length so it can be mocked in tests and swapped for Spring AI — or a
 * self-hosted model — later without touching the categorizer.
 */
public interface AnthropicChatClient {

    /** Send a system + user prompt, return the model's text response. */
    String complete(String system, String user);
}
