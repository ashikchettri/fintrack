package com.fintrack.finance.service.csv;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * A canonical transaction field and the header names banks use for it. Matching
 * is done on a normalized header (lowercased, with spaces/underscores/hyphens
 * removed), so "Transaction Date", "transaction_date" and "Posted Date" all map
 * to {@link #DATE}. This is what lets one importer swallow many banks' exports.
 */
public enum CsvColumn {

    DATE("transactiondate", "date", "posteddate", "postingdate", "valuedate"),
    DESCRIPTION("details", "description", "narrative", "payee", "memo", "name"),
    ACCOUNT("account", "accountname"),
    CATEGORY("category"),
    SUBCATEGORY("subcategory"),
    TAGS("tags", "tag"),
    NOTES("notes", "note"),
    DEBIT("debit", "withdrawal", "withdrawals", "moneyout", "paidout", "outgoing"),
    CREDIT("credit", "deposit", "deposits", "moneyin", "paidin", "incoming"),
    AMOUNT("amount", "value"),
    ORIGINAL_DESCRIPTION("originaldescription", "rawdescription", "bankdescription");

    private final Set<String> aliases;

    CsvColumn(String... aliases) {
        this.aliases = Set.of(aliases);
    }

    boolean matches(String normalizedHeader) {
        return aliases.contains(normalizedHeader);
    }

    /** lowercase and strip separators so header variants compare equal. */
    static String normalize(String header) {
        if (header == null) {
            return "";
        }
        return header.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    /** The canonical column a header maps to, or {@code null} if unknown. */
    static CsvColumn resolve(String header) {
        String normalized = normalize(header);
        return Arrays.stream(values())
                .filter(c -> c.matches(normalized))
                .findFirst()
                .orElse(null);
    }
}
