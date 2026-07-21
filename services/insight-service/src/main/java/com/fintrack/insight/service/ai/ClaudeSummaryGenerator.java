package com.fintrack.insight.service.ai;

import com.fintrack.insight.service.SummaryGenerator;
import com.fintrack.insight.service.TemplateSummaryGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI summary generator (ADR 012): sends the month's aggregate figures to Claude
 * for a natural-language headline + insights. Falls back to the deterministic
 * template on any error or malformed response, so a summary always comes back
 * and the AI can only improve it.
 */
public class ClaudeSummaryGenerator implements SummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClaudeSummaryGenerator.class);

    private final AnthropicChatClient client;
    private final TemplateSummaryGenerator fallback;
    private final ObjectMapper mapper;

    public ClaudeSummaryGenerator(AnthropicChatClient client, TemplateSummaryGenerator fallback,
                                  ObjectMapper mapper) {
        this.client = client;
        this.fallback = fallback;
        this.mapper = mapper;
    }

    @Override
    public MonthlySummary summarize(SummaryInput input) {
        if (input.transactionCount() == 0) {
            return fallback.summarize(input);   // nothing to narrate
        }
        try {
            String response = client.complete(SYSTEM_PROMPT, userPrompt(input));
            Parsed parsed = mapper.readValue(extractJsonObject(response), Parsed.class);
            if (parsed.headline() == null || parsed.headline().isBlank()) {
                return fallback.summarize(input);
            }
            return new MonthlySummary(parsed.headline().strip(),
                    parsed.insights() == null ? List.of() : parsed.insights());
        } catch (Exception e) {
            log.warn("AI summary failed, using the template: {}", e.toString());
            return fallback.summarize(input);
        }
    }

    private String userPrompt(SummaryInput in) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", in.month());
        payload.put("currency", in.currency());
        payload.put("income", in.income());
        payload.put("expenses", in.expenses());
        payload.put("net", in.net());
        payload.put("transactionCount", in.transactionCount());
        List<Map<String, Object>> cats = new ArrayList<>();
        for (CategoryShare c : in.topCategories()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", c.category());
            m.put("spent", c.spent());
            m.put("share", c.share());
            cats.add(m);
        }
        payload.put("topCategories", cats);
        return mapper.writeValueAsString(payload);
    }

    private static String extractJsonObject(String text) {
        if (text == null) {
            return "{}";
        }
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        return open >= 0 && close > open ? text.substring(open, close + 1) : "{}";
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Parsed(String headline, List<String> insights) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a household finance assistant. Given a month's aggregate spending figures as JSON,
            write a brief, friendly summary. Respond with ONLY a JSON object, no prose:
            {"headline": "<one sentence>", "insights": ["<short insight>", "..."]}.
            Keep it to a headline and 2-4 concise, specific insights grounded in the numbers.
            Never invent figures or categories not present in the input.""";
}
