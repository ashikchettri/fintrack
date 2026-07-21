package com.fintrack.insight.service.ai;

/**
 * Thin port over the Anthropic Messages API (ADR 012, mirroring ADR 009). Keeps
 * the model call mockable and swappable for Spring AI later.
 */
import java.util.List;
import java.util.Map;

public interface AnthropicChatClient {

    /** Send a system + user prompt, return the model's text response. */
    String complete(String system, String user);

    /**
     * A tool-enabled Messages call for the Q&A loop (ADR 013). {@code tools} and
     * {@code messages} are the Anthropic request shapes (built by the caller);
     * returns the raw response so the loop can act on tool_use blocks.
     */
    ClaudeResponse converse(String system, List<Map<String, Object>> tools,
                            List<Map<String, Object>> messages);
}
