package com.fintrack.insight.service;

import com.fintrack.insight.service.ai.AnthropicChatClient;
import com.fintrack.insight.service.ai.ClaudeResponse;
import com.fintrack.insight.service.ai.ClaudeResponse.ContentBlock;
import com.fintrack.insight.web.dto.AnswerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Natural-language Q&A via a Claude tool-use loop (ADR 013): the model decides
 * what data it needs, we fetch it from finance-service (forwarding the caller's
 * JWT), feed it back, and repeat until it answers. AI-required — no deterministic
 * fallback for arbitrary questions.
 */
@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    /** Bound the loop so a runaway conversation can't spin forever. */
    private static final int MAX_TURNS = 5;

    private final ObjectProvider<AnthropicChatClient> clientProvider;
    private final FinanceClient financeClient;
    private final ObjectMapper mapper;

    public QuestionService(ObjectProvider<AnthropicChatClient> clientProvider,
                           FinanceClient financeClient, ObjectMapper mapper) {
        this.clientProvider = clientProvider;
        this.financeClient = financeClient;
        this.mapper = mapper;
    }

    public AnswerResponse ask(String bearerToken, String question) {
        AnthropicChatClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new AiNotConfiguredException("Ask requires AI to be configured (insight.ai.enabled + a key).");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", question));

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            ClaudeResponse response = client.converse(SYSTEM_PROMPT, TOOLS, messages);

            if (!"tool_use".equals(response.stopReason())) {
                String answer = response.text();
                return new AnswerResponse(question, answer.isBlank()
                        ? "I couldn't find an answer to that in your data." : answer.strip());
            }

            // echo the assistant's turn verbatim, then answer each tool call
            messages.add(Map.of("role", "assistant", "content", assistantContent(response.content())));
            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (ContentBlock toolUse : response.toolUses()) {
                toolResults.add(Map.of(
                        "type", "tool_result",
                        "tool_use_id", toolUse.id(),
                        "content", runTool(toolUse, bearerToken)));
            }
            messages.add(Map.of("role", "user", "content", toolResults));
        }

        log.warn("Q&A hit the {}-turn cap without a final answer", MAX_TURNS);
        return new AnswerResponse(question, "That took more steps than I can work through — try a narrower question.");
    }

    private List<Map<String, Object>> assistantContent(List<ContentBlock> blocks) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (ContentBlock b : blocks) {
            if ("text".equals(b.type()) && b.text() != null) {
                content.add(Map.of("type", "text", "text", b.text()));
            } else if ("tool_use".equals(b.type())) {
                content.add(Map.of("type", "tool_use", "id", b.id(), "name", b.name(),
                        "input", b.input() == null ? Map.of() : b.input()));
            }
        }
        return content;
    }

    /** Execute a tool call and return its result as a JSON string for the model. */
    private String runTool(ContentBlock toolUse, String bearerToken) {
        if (!"get_spending".equals(toolUse.name())) {
            return "{\"error\": \"unknown tool\"}";
        }
        Object month = toolUse.input() == null ? null : toolUse.input().get("month");
        FinanceDashboard dashboard = financeClient.dashboard(bearerToken, month == null ? null : month.toString());
        return mapper.writeValueAsString(dashboard);
    }

    private static final List<Map<String, Object>> TOOLS = List.of(Map.of(
            "name", "get_spending",
            "description", "Get the caller's spending: totals, spend by canonical category, top merchants, "
                    + "and the list of available months. Pass month as YYYY-MM for one month, or omit for all-time.",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("month", Map.of(
                            "type", "string",
                            "description", "Month as YYYY-MM; omit for all-time and the available-months list.")))));

    private static final String SYSTEM_PROMPT = """
            You are a household finance assistant. Answer the user's question about their spending using ONLY
            the get_spending tool to fetch real figures — never invent or estimate numbers. Amounts are in the
            account's currency. Categories are canonical: "Groceries & Food" covers food/groceries/dining,
            "Transport" covers fuel/rideshare/public transport, etc. If the data doesn't answer the question,
            say so plainly. Be concise and specific, and cite the figures you used.""";
}
