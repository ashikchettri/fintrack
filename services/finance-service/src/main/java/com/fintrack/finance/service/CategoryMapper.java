package com.fintrack.finance.service;

import com.fintrack.finance.domain.SpendingCategory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps both sides of the app onto the canonical {@link SpendingCategory} (ADR 008):
 * budget group labels and free-text bank categories. One place for all category
 * normalisation so the plan and the actuals speak the same language.
 *
 * <p>{@link #fromBankCategory} is the seam Phase 4 replaces: the AI will emit a
 * {@code SpendingCategory} for each transaction, written to the same column and
 * evaluated against the same enum — the keyword rules below are the v1 baseline.
 */
public final class CategoryMapper {

    private CategoryMapper() {
    }

    // Budget group label (normalised) → canonical. The template groups match the
    // enum labels exactly; the alias handles a couple of common rewordings.
    private static final Map<String, SpendingCategory> BUDGET_GROUPS = buildBudgetGroups();

    private static Map<String, SpendingCategory> buildBudgetGroups() {
        var map = new java.util.HashMap<String, SpendingCategory>();
        for (SpendingCategory c : SpendingCategory.values()) {
            map.put(normalise(c.label()), c);
        }
        // aliases for hand-typed budget groups that don't match a label verbatim
        map.put("utilities", SpendingCategory.UTILITIES);
        map.put("groceries", SpendingCategory.GROCERIES);
        map.put("food", SpendingCategory.GROCERIES);
        map.put("health", SpendingCategory.HEALTH);
        map.put("insurance", SpendingCategory.INSURANCE_FINANCIAL);
        map.put("kids", SpendingCategory.KIDS_FAMILY);
        map.put("subscriptions", SpendingCategory.SUBSCRIPTIONS);
        map.put("entertainment", SpendingCategory.SUBSCRIPTIONS);
        map.put("personal", SpendingCategory.PERSONAL);
        return Map.copyOf(map);
    }

    // Ordered keyword rules for free-text bank categories + descriptions. First
    // match wins, so put the more specific buckets before the generic ones.
    private record Rule(SpendingCategory category, List<String> keywords) {
    }

    private static final List<Rule> BANK_RULES = List.of(
            new Rule(SpendingCategory.INCOME,
                    List.of("salary", "payroll", "wage", "income", "pay ", "dividend", "interest earned")),
            new Rule(SpendingCategory.GROCERIES,
                    List.of("grocer", "supermarket", "woolworths", "coles", "aldi", "food", "dining",
                            "restaurant", "cafe", "coffee", "takeaway", "eat", "bakery")),
            new Rule(SpendingCategory.TRANSPORT,
                    List.of("transport", "fuel", "petrol", "gas station", "uber", "taxi", "parking",
                            "toll", "car ", "automotive", "rideshare", "public transport", "train", "bus")),
            new Rule(SpendingCategory.UTILITIES,
                    List.of("utilit", "electric", "water", "internet", "nbn", "phone", "mobile",
                            "telecom", "bill", "energy")),
            new Rule(SpendingCategory.HOUSING,
                    List.of("rent", "mortgage", "housing", "home", "hardware", "council rate", "strata")),
            new Rule(SpendingCategory.KIDS_FAMILY,
                    List.of("childcare", "daycare", "kids", "school", "baby", "family", "tuition")),
            new Rule(SpendingCategory.HEALTH,
                    List.of("health", "medical", "pharmacy", "chemist", "dental", "doctor", "gp ",
                            "fitness", "gym")),
            new Rule(SpendingCategory.INSURANCE_FINANCIAL,
                    List.of("insurance", "bank fee", "loan", "interest charged", "financ", "credit card")),
            new Rule(SpendingCategory.SUBSCRIPTIONS,
                    List.of("subscription", "streaming", "netflix", "spotify", "entertainment",
                            "movie", "cinema", "game", "software", "cloud")),
            new Rule(SpendingCategory.SAVINGS,
                    List.of("saving", "investment", "transfer to", "deposit")),
            new Rule(SpendingCategory.PERSONAL,
                    List.of("shopping", "clothing", "apparel", "personal care", "hair", "beauty",
                            "gift", "pet", "department")));

    /** A budget line's group heading → canonical (OTHER when unrecognised). */
    public static SpendingCategory fromBudgetGroup(String group) {
        if (group == null || group.isBlank()) {
            return SpendingCategory.OTHER;
        }
        return BUDGET_GROUPS.getOrDefault(normalise(group), SpendingCategory.OTHER);
    }

    /**
     * A bank export's free-text category (plus the description for extra signal)
     * → canonical. Keyword rules, first match wins; OTHER when nothing matches.
     */
    public static SpendingCategory fromBankCategory(String category, String description) {
        String haystack = normalise((category == null ? "" : category) + " " + (description == null ? "" : description));
        if (haystack.isBlank()) {
            return SpendingCategory.OTHER;
        }
        for (Rule rule : BANK_RULES) {
            for (String keyword : rule.keywords()) {
                if (haystack.contains(keyword)) {
                    return rule.category();
                }
            }
        }
        return SpendingCategory.OTHER;
    }

    /**
     * The canonical category already stored on a row, or a live derivation from
     * the raw bank fields when the column is null (rows imported before ADR 008).
     */
    public static SpendingCategory resolve(String storedCanonical, String bankCategory, String description) {
        if (storedCanonical != null && !storedCanonical.isBlank()) {
            try {
                return SpendingCategory.valueOf(storedCanonical);
            } catch (IllegalArgumentException ignored) {
                // fall through to a live derivation if the stored value is stale
            }
        }
        return fromBankCategory(bankCategory, description);
    }

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }
}
