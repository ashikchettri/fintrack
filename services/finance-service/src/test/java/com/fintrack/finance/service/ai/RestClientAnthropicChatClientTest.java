package com.fintrack.finance.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class RestClientAnthropicChatClientTest {

    @Test
    void postsToTheMessagesApiAndExtractsTheText() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", "sk-test")
                .defaultHeader("anthropic-version", "2023-06-01");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(POST))
                .andExpect(header("x-api-key", "sk-test"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-haiku-4-5-20251001"))
                .andExpect(jsonPath("$.system").value("SYS"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("USER"))
                .andRespond(withSuccess(
                        """
                        {"content": [{"type": "text", "text": "[{\\"i\\":0,\\"category\\":\\"GROCERIES\\"}]"}]}""",
                        MediaType.APPLICATION_JSON));

        var client = new RestClientAnthropicChatClient(builder.build(), "claude-haiku-4-5-20251001");
        String text = client.complete("SYS", "USER");

        assertThat(text).isEqualTo("[{\"i\":0,\"category\":\"GROCERIES\"}]");
        server.verify();
    }
}
