package com.fintrack.insight.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A Claude Messages response for the tool-use loop (ADR 013). {@code stopReason}
 * of {@code "tool_use"} means the model wants a tool run; anything else ends the
 * turn. Content blocks are either {@code text} or {@code tool_use}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeResponse(
        @JsonProperty("stop_reason") String stopReason,
        List<ContentBlock> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            String type,
            String text,
            String id,
            String name,
            Map<String, Object> input) {
    }

    /** All text blocks joined — the model's prose answer. */
    public String text() {
        if (content == null) {
            return "";
        }
        return content.stream()
                .filter(b -> "text".equals(b.type()) && b.text() != null)
                .map(ContentBlock::text)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    /** The tool_use blocks the model emitted this turn. */
    public List<ContentBlock> toolUses() {
        return content == null ? List.of()
                : content.stream().filter(b -> "tool_use".equals(b.type())).toList();
    }
}
