package com.fintrack.finance.service;

import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.domain.SpendingCategory;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.ai.TransactionCategorizer;
import com.fintrack.finance.service.ai.TransactionCategorizer.CategorizationInput;
import com.fintrack.finance.service.csv.CsvParseResult;
import com.fintrack.finance.service.csv.CsvTransactionParser;
import com.fintrack.finance.service.csv.ParsedTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The hero flow: a bank CSV in, a member's transactions out. Everything is
 * scoped to the caller's household + member (from the verified JWT), the
 * statement's "Account" column is resolved to a real account (created on the
 * fly), and each row is deduplicated so re-uploading the same or an overlapping
 * statement never double-counts.
 */
@Service
public class TransactionImportService {

    /** Account name used when the statement has no Account column. */
    static final String DEFAULT_ACCOUNT_NAME = "Imported";

    private final CsvTransactionParser parser;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategorizer categorizer;

    public TransactionImportService(CsvTransactionParser parser,
                                    AccountRepository accountRepository,
                                    TransactionRepository transactionRepository,
                                    TransactionCategorizer categorizer) {
        this.parser = parser;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categorizer = categorizer;
    }

    /** A row that survived dedup, awaiting categorization + persistence. */
    private record Pending(Account account, ParsedTransaction row, String hash) {
    }

    @Transactional
    public ImportSummary importCsv(AuthenticatedMember caller, String fileName,
                                   String currency, InputStream csv) {
        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        UUID importId = UUID.randomUUID();
        CsvParseResult parsed = parser.parse(csv);

        // one round-trip for the member's prior digests; the whole batch dedups in memory
        Set<String> seenHashes = new HashSet<>(
                transactionRepository.findDedupHashes(caller.householdId(), caller.memberId()));
        Map<String, Account> accountsByName = new HashMap<>();
        List<String> accountsCreated = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();

        List<Pending> pending = new ArrayList<>();
        int duplicates = 0;
        for (ParsedTransaction row : parsed.rows()) {
            Account account = resolveAccount(caller, row.accountName(), normalizedCurrency,
                    accountsByName, accountsCreated);

            String hash = dedupHash(account.getId(), row, occurrences);
            if (!seenHashes.add(hash)) {   // add() is false if already present → duplicate
                duplicates++;
                continue;
            }
            pending.add(new Pending(account, row, hash));
        }

        // one batched categorization pass over the surviving rows (ADR 009)
        List<SpendingCategory> categories = categorizer.categorize(pending.stream()
                .map(p -> new CategorizationInput(p.row().description(), p.row().category(), p.row().amount()))
                .toList());

        List<Transaction> toSave = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            Pending p = pending.get(i);
            toSave.add(toEntity(caller, p.account(), p.row(), normalizedCurrency, importId, p.hash(),
                    categories.get(i)));
        }
        transactionRepository.saveAll(toSave);

        return summarize(importId, fileName, normalizedCurrency, parsed, toSave,
                duplicates, accountsCreated);
    }

    private Account resolveAccount(AuthenticatedMember caller, String rawName, String currency,
                                   Map<String, Account> cache, List<String> created) {
        String name = (rawName == null || rawName.isBlank())
                ? DEFAULT_ACCOUNT_NAME
                : truncate(rawName.strip(), 100);
        String key = name.toLowerCase(Locale.ROOT);
        Account cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Account account = accountRepository
                .findByHouseholdIdAndMemberIdAndNameIgnoreCase(caller.householdId(), caller.memberId(), name)
                .orElseGet(() -> {
                    Account fresh = accountRepository.save(new Account(
                            caller.householdId(), caller.memberId(), name,
                            AccountType.OTHER, currency, BigDecimal.ZERO));
                    created.add(name);
                    return fresh;
                });
        cache.put(key, account);
        return account;
    }

    private Transaction toEntity(AuthenticatedMember caller, Account account, ParsedTransaction row,
                                 String currency, UUID importId, String hash, SpendingCategory canonical) {
        return Transaction.builder()
                .householdId(caller.householdId())
                .memberId(caller.memberId())
                .accountId(account.getId())
                .txnDate(row.date())
                .description(truncate(row.description(), 200))
                .category(truncate(row.category(), 60))
                .subcategory(truncate(row.subcategory(), 60))
                // canonical vocabulary (ADR 008) via the categorizer seam (ADR 009):
                // rule-based by default, AI when enabled
                .canonicalCategory(canonical.name())
                .amount(row.amount())
                .currency(currency)
                .originalDescription(truncate(row.originalDescription(), 300))
                .tags(truncate(row.tags(), 200))
                .notes(truncate(row.notes(), 500))
                .visibility(Visibility.PERSONAL)   // ADR 001: imports never auto-share
                .source(TransactionSource.CSV_IMPORT)
                .importId(importId)
                .dedupHash(hash)
                .build();
    }

    /**
     * SHA-256 over the row's natural key. An occurrence counter disambiguates
     * genuinely-identical rows within one file (e.g. two identical fuel stops the
     * same day) so both are kept on first import, yet a re-upload of the same file
     * lines the occurrences up again and dedups every row.
     */
    private String dedupHash(UUID accountId, ParsedTransaction row, Map<String, Integer> occurrences) {
        String descriptor = row.originalDescription() != null ? row.originalDescription() : row.description();
        String base = accountId + "|" + row.date() + "|" + row.amount().toPlainString() + "|" + descriptor;
        int occurrence = occurrences.merge(base, 1, Integer::sum) - 1;
        return sha256(base + "#" + occurrence);
    }

    private ImportSummary summarize(UUID importId, String fileName, String currency,
                                    CsvParseResult parsed, List<Transaction> saved,
                                    int duplicates, List<String> accountsCreated) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        LocalDate from = null;
        LocalDate to = null;
        for (Transaction t : saved) {
            if (t.getAmount().signum() >= 0) {
                income = income.add(t.getAmount());
            } else {
                expenses = expenses.add(t.getAmount());
            }
            if (from == null || t.getTxnDate().isBefore(from)) {
                from = t.getTxnDate();
            }
            if (to == null || t.getTxnDate().isAfter(to)) {
                to = t.getTxnDate();
            }
        }
        return new ImportSummary(importId, fileName, currency,
                parsed.rows().size(), saved.size(), duplicates, parsed.errors().size(),
                List.copyOf(accountsCreated), from, to,
                income, expenses.abs(), income.add(expenses), parsed.errors());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);   // never on a JVM
        }
    }
}
