package com.fintrack.insight.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The always-present default (ADR 012): a deterministic summary composed from
 * the numbers. It's the floor the AI generator falls back to, and the sole
 * generator when AI is off — so the service runs with no API key.
 */
@Component
public class TemplateSummaryGenerator implements SummaryGenerator {

    @Override
    public MonthlySummary summarize(SummaryInput in) {
        if (in.transactionCount() == 0) {
            return new MonthlySummary("No transactions to summarize for %s yet.".formatted(period(in.month())),
                    List.of());
        }

        String headline = "In %s you spent %s across %d transaction%s.".formatted(
                period(in.month()), money(in.expenses(), in.currency()),
                in.transactionCount(), in.transactionCount() == 1 ? "" : "s");

        List<String> insights = new ArrayList<>();
        BigDecimal net = in.net();
        if (net.signum() >= 0) {
            insights.add("You came out ahead by %s.".formatted(money(net, in.currency())));
        } else {
            insights.add("You spent %s more than you earned.".formatted(money(net.abs(), in.currency())));
        }
        if (!in.topCategories().isEmpty()) {
            CategoryShare top = in.topCategories().get(0);
            insights.add("%s was your biggest category at %s (%d%% of spend).".formatted(
                    top.category(), money(top.spent(), in.currency()), Math.round(top.share() * 100)));
        }
        if (in.topCategories().size() > 1) {
            CategoryShare second = in.topCategories().get(1);
            insights.add("Next was %s at %s.".formatted(second.category(), money(second.spent(), in.currency())));
        }
        return new MonthlySummary(headline, insights);
    }

    /** "2026-06" → "June 2026"; null → "this period". */
    static String period(String month) {
        if (month == null) {
            return "this period";
        }
        String[] parts = month.split("-");
        if (parts.length != 2) {
            return month;
        }
        try {
            String name = Month.of(Integer.parseInt(parts[1])).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            return name + " " + parts[0];
        } catch (RuntimeException e) {
            return month;
        }
    }

    static String money(BigDecimal amount, String currency) {
        return "%s %,.2f".formatted(currency == null ? "AUD" : currency, amount);
    }
}
