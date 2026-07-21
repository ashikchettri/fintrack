package com.fintrack.insight.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Anthropic's {@code POST /v1/messages} over Spring {@link RestClient}
 * (ADR 012). Deserializes into records so it's Jackson-version agnostic and
 * tolerant of the response fields we don't use.
 */
public class RestClientAnthropicChatClient implements AnthropicChatClient {

    private static final int MAX_TOKENS = 1024;

    private final RestClient http;
    private final String model;

    public RestClientAnthropicChatClient(RestClient http, String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));

        AnthropicResponse response = http.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(AnthropicResponse.class);

        if (response == null || response.content() == null || response.content().isEmpty()) {
            return "";
        }
        String text = response.content().get(0).text();
        return text == null ? "" : text;
    }

    @Override
    public ClaudeResponse converse(String system, List<Map<String, Object>> tools,
                                   List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", system);
        body.put("tools", tools);
        body.put("messages", messages);

        ClaudeResponse response = http.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(ClaudeResponse.class);

        return response == null ? new ClaudeResponse("end_turn", List.of()) : response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicResponse(List<ContentBlock> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {
    }
}
