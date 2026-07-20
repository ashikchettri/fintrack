package com.fintrack.finance.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.finance.domain.SpendingCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI categorizer (ADR 009): asks Claude to classify each transaction into the
 * canonical {@link SpendingCategory} enum, in batches. The rule-based result is
 * the floor — every row starts rule-categorized and the AI only overwrites the
 * ones it confidently labels. Any failure (transport, malformed JSON, unknown
 * category) silently leaves that row's rule-based category in place, so AI can
 * lift accuracy but never break an import.
 */
public class ClaudeCategorizer implements TransactionCategorizer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCategorizer.class);

    private final AnthropicChatClient client;
    private final RuleBasedCategorizer fallback;
    private final ObjectMapper mapper;
    private final int batchSize;

    public ClaudeCategorizer(AnthropicChatClient client, RuleBasedCategorizer fallback,
                             ObjectMapper mapper, int batchSize) {
        this.client = client;
        this.fallback = fallback;
        this.mapper = mapper;
        this.batchSize = Math.max(1, batchSize);
    }

    @Override
    public List<SpendingCategory> categorize(List<CategorizationInput> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        // start from the rule-based floor, then overlay the AI's confident calls
        List<SpendingCategory> result = new ArrayList<>(fallback.categorize(inputs));

        for (int start = 0; start < inputs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, inputs.size());
            List<CategorizationInput> chunk = inputs.subList(start, end);
            try {
                SpendingCategory[] aiCats = classifyChunk(chunk);
                for (int i = 0; i < aiCats.length; i++) {
                    if (aiCats[i] != null) {
                        result.set(start + i, aiCats[i]);
                    }
                }
            } catch (Exception e) {
                // keep the rule-based categories for this chunk
                log.warn("AI categorization failed for rows {}–{}, keeping rule-based: {}",
                        start, end, e.toString());
            }
        }
        return result;
    }

    /** One model round-trip; returns a category per chunk row (null where the model didn't help). */
    private SpendingCategory[] classifyChunk(List<CategorizationInput> chunk) {
        String response = client.complete(SYSTEM_PROMPT, userPrompt(chunk));

        SpendingCategory[] out = new SpendingCategory[chunk.size()];
        Assignment[] assignments = mapper.readValue(extractJsonArray(response), Assignment[].class);
        for (Assignment a : assignments) {
            if (a == null || a.i() < 0 || a.i() >= out.length) {
                continue;
            }
            out[a.i()] = parseCategory(a.category());
        }
        return out;
    }

    private String userPrompt(List<CategorizationInput> chunk) {
        List<Map<String, Object>> rows = new ArrayList<>(chunk.size());
        for (int i = 0; i < chunk.size(); i++) {
            CategorizationInput in = chunk.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("i", i);
            row.put("description", in.description() == null ? "" : in.description());
            row.put("bankCategory", in.bankCategory() == null ? "" : in.bankCategory());
            // only the sign leaves the boundary, never the amount (ADR 009)
            row.put("direction", in.amount() != null && in.amount().signum() >= 0 ? "credit" : "debit");
            rows.add(row);
        }
        return mapper.writeValueAsString(rows);
    }

    private static SpendingCategory parseCategory(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return SpendingCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;   // unknown label → fall back for this row
        }
    }

    /** Pull the first JSON array out of the model text, tolerating any prose around it. */
    private static String extractJsonArray(String text) {
        if (text == null) {
            return "[]";
        }
        int open = text.indexOf('[');
        int close = text.lastIndexOf(']');
        return open >= 0 && close > open ? text.substring(open, close + 1) : "[]";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Assignment(int i, String category) {
    }

    private static final String SYSTEM_PROMPT = buildSystemPrompt();

    private static String buildSystemPrompt() {
        String categories = Arrays.stream(SpendingCategory.values())
                .map(c -> c.name() + " (" + c.label() + ")")
                .collect(Collectors.joining(", "));
        return """
                You categorize personal bank transactions for a household finance app.
                Classify each input into EXACTLY ONE of these canonical categories:
                %s.
                Use INCOME for money coming in (credit), SAVINGS for transfers to savings/investments,
                and OTHER only when genuinely unclear. Base the decision on the description and the
                bank's category label. Respond with ONLY a JSON array, no prose, one object per input:
                [{"i": <the input's i>, "category": "<CATEGORY_ENUM_NAME>"}].""".formatted(categories);
    }
}
