package com.fintrack.insight.service.ai;

/**
 * Thin port over the Anthropic Messages API (ADR 012, mirroring ADR 009). Keeps
 * the model call mockable and swappable for Spring AI later.
 */
public interface AnthropicChatClient {

    /** Send a system + user prompt, return the model's text response. */
    String complete(String system, String user);
}
