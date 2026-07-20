package com.fintrack.finance.service;

import com.fintrack.finance.domain.BudgetFrequency;
import com.fintrack.finance.domain.BudgetSection;
import com.fintrack.finance.web.dto.BudgetLineDto;

import java.util.ArrayList;
import java.util.List;

import static com.fintrack.finance.domain.BudgetFrequency.ANNUALLY;
import static com.fintrack.finance.domain.BudgetFrequency.FORTNIGHTLY;
import static com.fintrack.finance.domain.BudgetFrequency.MONTHLY;
import static com.fintrack.finance.domain.BudgetFrequency.QUARTERLY;
import static com.fintrack.finance.domain.BudgetFrequency.WEEKLY;

/**
 * The starter household budget (from the reference spreadsheet) — returned when
 * a household hasn't saved one yet, so members see the familiar layout with
 * empty amounts to fill in. Fully editable afterwards.
 */
final class BudgetTemplate {

    private BudgetTemplate() {
    }

    static List<BudgetLineDto> defaultLines() {
        List<BudgetLineDto> lines = new ArrayList<>();

        income(lines, "My take-home salary (net)", MONTHLY);
        income(lines, "Partner's take-home salary (net)", MONTHLY);
        income(lines, "Bonus", ANNUALLY);

        expense(lines, "Housing", "Mortgage repayments", WEEKLY);
        expense(lines, "Housing", "Council rates", ANNUALLY);
        expense(lines, "Housing", "Stamp duty / property tax", ANNUALLY);
        expense(lines, "Housing", "Home & contents insurance", ANNUALLY);
        expense(lines, "Housing", "Home maintenance & repairs", MONTHLY);

        expense(lines, "Utilities & Communications", "Electricity", QUARTERLY);
        expense(lines, "Utilities & Communications", "Gas", QUARTERLY);
        expense(lines, "Utilities & Communications", "Water", QUARTERLY);
        expense(lines, "Utilities & Communications", "Internet (NBN)", MONTHLY);
        expense(lines, "Utilities & Communications", "Mobile phones", MONTHLY);

        expense(lines, "Groceries & Food", "Groceries", WEEKLY);
        expense(lines, "Groceries & Food", "Dining out & takeaway", WEEKLY);
        expense(lines, "Groceries & Food", "Coffee", WEEKLY);

        expense(lines, "Transport", "Fuel / charging", FORTNIGHTLY);
        expense(lines, "Transport", "Car registration", ANNUALLY);
        expense(lines, "Transport", "Car insurance", ANNUALLY);
        expense(lines, "Transport", "Car servicing & repairs", ANNUALLY);
        expense(lines, "Transport", "Public transport", WEEKLY);
        expense(lines, "Transport", "Tolls", MONTHLY);

        expense(lines, "Kids & Family", "Childcare / daycare", WEEKLY);
        expense(lines, "Kids & Family", "School fees", ANNUALLY);
        expense(lines, "Kids & Family", "Kids' activities", MONTHLY);
        expense(lines, "Kids & Family", "Baby supplies (nappies, formula)", WEEKLY);

        expense(lines, "Health & Wellbeing", "Private health insurance", MONTHLY);
        expense(lines, "Health & Wellbeing", "Medical / GP / dental", MONTHLY);
        expense(lines, "Health & Wellbeing", "Life insurance", MONTHLY);
        expense(lines, "Health & Wellbeing", "Gym / fitness", FORTNIGHTLY);

        expense(lines, "Insurance & Financial", "Income protection insurance", MONTHLY);
        expense(lines, "Insurance & Financial", "Credit card 1 repayment", MONTHLY);
        expense(lines, "Insurance & Financial", "Credit card 2 repayment", MONTHLY);
        expense(lines, "Insurance & Financial", "Bank fees & loan interest", MONTHLY);

        expense(lines, "Subscriptions & Entertainment", "Streaming (Netflix, Spotify, etc.)", MONTHLY);
        expense(lines, "Subscriptions & Entertainment", "Software & cloud subscriptions", MONTHLY);
        expense(lines, "Subscriptions & Entertainment", "Entertainment & outings", MONTHLY);

        expense(lines, "Personal & Miscellaneous", "Clothing & shoes", MONTHLY);
        expense(lines, "Personal & Miscellaneous", "Personal care & haircuts", MONTHLY);
        expense(lines, "Personal & Miscellaneous", "Gifts & celebrations", MONTHLY);
        expense(lines, "Personal & Miscellaneous", "Pet costs", MONTHLY);
        expense(lines, "Personal & Miscellaneous", "Miscellaneous / buffer", MONTHLY);

        saving(lines, "Regular savings / emergency fund", MONTHLY);
        saving(lines, "Extra mortgage repayments", MONTHLY);
        saving(lines, "Investments (shares, super top-up)", MONTHLY);

        return lines;
    }

    private static void income(List<BudgetLineDto> lines, String name, BudgetFrequency freq) {
        lines.add(new BudgetLineDto(BudgetSection.INCOME, null, name, freq, null));
    }

    private static void expense(List<BudgetLineDto> lines, String category, String name, BudgetFrequency freq) {
        lines.add(new BudgetLineDto(BudgetSection.EXPENSE, category, name, freq, null));
    }

    private static void saving(List<BudgetLineDto> lines, String name, BudgetFrequency freq) {
        lines.add(new BudgetLineDto(BudgetSection.SAVING, null, name, freq, null));
    }
}
