package com.fintrack.insight.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientAnthropicChatClientTest {

    @Test
    void postsToTheMessagesApiAndExtractsTheText() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", "sk-test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(POST))
                .andExpect(header("x-api-key", "sk-test"))
                .andExpect(jsonPath("$.model").value("claude-haiku-4-5-20251001"))
                .andExpect(jsonPath("$.system").value("SYS"))
                .andExpect(jsonPath("$.messages[0].content").value("USER"))
                .andRespond(withSuccess(
                        "{\"content\": [{\"type\": \"text\", \"text\": \"hello\"}]}", MediaType.APPLICATION_JSON));

        var client = new RestClientAnthropicChatClient(builder.build(), "claude-haiku-4-5-20251001");

        assertThat(client.complete("SYS", "USER")).isEqualTo("hello");
        server.verify();
    }

    @Test
    void converseSendsToolsAndParsesAToolUseResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.tools[0].name").value("get_spending"))
                .andExpect(jsonPath("$.messages[0].content").value("how much?"))
                .andRespond(withSuccess("""
                        {"stop_reason": "tool_use",
                         "content": [{"type": "tool_use", "id": "tu_1", "name": "get_spending",
                                      "input": {"month": "2026-06"}}]}""", MediaType.APPLICATION_JSON));

        var client = new RestClientAnthropicChatClient(builder.build(), "claude-haiku-4-5-20251001");
        var response = client.converse("SYS",
                java.util.List.of(java.util.Map.of("name", "get_spending")),
                java.util.List.of(java.util.Map.of("role", "user", "content", "how much?")));

        assertThat(response.stopReason()).isEqualTo("tool_use");
        assertThat(response.toolUses()).singleElement()
                .satisfies(b -> {
                    assertThat(b.name()).isEqualTo("get_spending");
                    assertThat(b.input()).containsEntry("month", "2026-06");
                });
        server.verify();
    }
}
