package com.fintrack.finance.service.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a bank-export CSV into normalized {@link ParsedTransaction} rows,
 * tolerating the format differences between banks:
 *
 * <ul>
 *   <li>flexible headers via {@link CsvColumn} (Details vs Description, …);</li>
 *   <li>separate Debit/Credit columns <em>or</em> a single signed Amount;</li>
 *   <li>several date formats (ISO, "11 Jul 2026", "11/07/2026");</li>
 *   <li>money with currency symbols, thousands separators, or (parenthesised)
 *       negatives.</li>
 * </ul>
 *
 * A row we can't read is skipped and reported (see {@link CsvParseResult}); a
 * file we can't read at all throws {@link CsvImportException}.
 */
@Component
public class CsvTransactionParser {

    // tried in order; first that fits wins. English locale for month names.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH));

    public CsvParseResult parse(InputStream in) {
        List<ParsedTransaction> rows = new ArrayList<>();
        List<CsvParseResult.RowError> errors = new ArrayList<>();

        // No setHeader(): we read every row and decide for ourselves whether the
        // first one is a header or already data. Many bank exports are
        // header-less (accounting/Quicken style: "date,amount,description" with
        // no column names), and assuming a header would eat the first transaction.
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<CSVRecord> records;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            records = parser.getRecords();
        } catch (IOException e) {
            throw new CsvImportException("Could not read the CSV file: " + e.getMessage(), e);
        }
        if (records.isEmpty()) {
            throw new CsvImportException("The CSV file has no rows.");
        }

        List<String> firstRow = Arrays.asList(records.get(0).values());
        Map<CsvColumn, Integer> columns;
        List<CSVRecord> dataRows;
        if (looksLikeHeader(firstRow)) {
            columns = mapColumns(firstRow);
            requireUsableColumns(columns, firstRow);
            dataRows = records.subList(1, records.size());
        } else {
            // header-less: infer date / amount / description from the data itself
            columns = inferColumns(records);
            dataRows = records;
        }

        for (CSVRecord record : dataRows) {
            try {
                rows.add(toTransaction(record, columns));
            } catch (RowParseException e) {
                errors.add(new CsvParseResult.RowError((int) record.getRecordNumber(), e.getMessage()));
            }
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new CsvImportException("The CSV file has no data rows.");
        }
        return new CsvParseResult(rows, errors);
    }

    /** A first row is a header iff its cells name a date and an amount column. */
    private boolean looksLikeHeader(List<String> firstRow) {
        Map<CsvColumn, Integer> resolved = mapColumns(firstRow);
        boolean hasAmount = resolved.containsKey(CsvColumn.AMOUNT)
                || resolved.containsKey(CsvColumn.DEBIT)
                || resolved.containsKey(CsvColumn.CREDIT);
        return resolved.containsKey(CsvColumn.DATE) && hasAmount;
    }

    /**
     * Infer columns for a header-less CSV by sniffing the data: which column
     * parses as dates, which as money, which is free text. Content-based (not
     * positional), so it handles "date,amount,description" and reordered variants
     * alike.
     */
    private Map<CsvColumn, Integer> inferColumns(List<CSVRecord> records) {
        int cols = records.get(0).size();
        int sample = Math.min(records.size(), 20);
        int[] dateHits = new int[cols];
        int[] numberHits = new int[cols];
        int[] textHits = new int[cols];
        int[] nonBlank = new int[cols];

        for (int r = 0; r < sample; r++) {
            CSVRecord record = records.get(r);
            for (int c = 0; c < cols && c < record.size(); c++) {
                String v = record.get(c);
                if (v == null || v.isBlank()) {
                    continue;
                }
                v = v.strip();
                nonBlank[c]++;
                if (isDate(v)) {
                    dateHits[c]++;
                } else if (looksNumeric(v)) {
                    numberHits[c]++;
                } else {
                    textHits[c]++;
                }
            }
        }

        int dateCol = majorityColumn(dateHits, nonBlank, -1);
        int amountCol = majorityColumn(numberHits, nonBlank, dateCol);
        int descCol = bestTextColumn(textHits, dateCol, amountCol);
        if (dateCol < 0 || amountCol < 0 || descCol < 0) {
            throw new CsvImportException(
                    "Couldn't detect a date, amount and description column in this header-less CSV.");
        }

        Map<CsvColumn, Integer> columns = new EnumMap<>(CsvColumn.class);
        columns.put(CsvColumn.DATE, dateCol);
        columns.put(CsvColumn.AMOUNT, amountCol);
        columns.put(CsvColumn.DESCRIPTION, descCol);
        return columns;
    }

    /** Lowest-index column (≠ exclude) whose metric is a majority of its cells. */
    private int majorityColumn(int[] hits, int[] nonBlank, int exclude) {
        for (int c = 0; c < hits.length; c++) {
            if (c != exclude && nonBlank[c] > 0 && hits[c] * 2 >= nonBlank[c]) {
                return c;
            }
        }
        return -1;
    }

    /** The most free-text column, excluding the date and amount columns. */
    private int bestTextColumn(int[] textHits, int dateCol, int amountCol) {
        int best = -1;
        int bestHits = 0;
        for (int c = 0; c < textHits.length; c++) {
            if (c != dateCol && c != amountCol && textHits[c] > bestHits) {
                bestHits = textHits[c];
                best = c;
            }
        }
        return best;
    }

    private boolean isDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDate.parse(value, format);
                return true;
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return false;
    }

    private boolean looksNumeric(String value) {
        String cleaned = value.replaceAll("[()\\s,$£€]", "");
        if (cleaned.isEmpty()) {
            return false;
        }
        try {
            new BigDecimal(cleaned);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Map<CsvColumn, Integer> mapColumns(List<String> headers) {
        Map<CsvColumn, Integer> columns = new EnumMap<>(CsvColumn.class);
        for (int i = 0; i < headers.size(); i++) {
            CsvColumn column = CsvColumn.resolve(headers.get(i));
            // first header wins if a bank repeats a canonical column
            if (column != null) {
                columns.putIfAbsent(column, i);
            }
        }
        return columns;
    }

    private void requireUsableColumns(Map<CsvColumn, Integer> columns, List<String> headers) {
        boolean hasAmount = columns.containsKey(CsvColumn.AMOUNT)
                || columns.containsKey(CsvColumn.DEBIT)
                || columns.containsKey(CsvColumn.CREDIT);
        boolean hasDescription = columns.containsKey(CsvColumn.DESCRIPTION)
                || columns.containsKey(CsvColumn.ORIGINAL_DESCRIPTION);
        if (!columns.containsKey(CsvColumn.DATE) || !hasAmount || !hasDescription) {
            throw new CsvImportException(
                    "The CSV needs at least a date, a description and an amount (or debit/credit) column. "
                            + "Found headers: " + headers);
        }
    }

    private ParsedTransaction toTransaction(CSVRecord record, Map<CsvColumn, Integer> columns) {
        LocalDate date = parseDate(value(record, columns, CsvColumn.DATE));
        BigDecimal amount = parseAmount(record, columns);

        String description = value(record, columns, CsvColumn.DESCRIPTION);
        String original = value(record, columns, CsvColumn.ORIGINAL_DESCRIPTION);
        if (description == null) {
            description = original;
        }
        if (description == null) {
            throw new RowParseException("row has no description");
        }

        return new ParsedTransaction(
                (int) record.getRecordNumber(),
                date,
                value(record, columns, CsvColumn.ACCOUNT),
                description,
                value(record, columns, CsvColumn.CATEGORY),
                value(record, columns, CsvColumn.SUBCATEGORY),
                value(record, columns, CsvColumn.TAGS),
                value(record, columns, CsvColumn.NOTES),
                amount,
                original);
    }

    /** Trimmed cell value, or {@code null} if the column is absent or blank. */
    private String value(CSVRecord record, Map<CsvColumn, Integer> columns, CsvColumn column) {
        Integer index = columns.get(column);
        if (index == null || index >= record.size()) {
            return null;
        }
        String raw = record.get(index);
        return (raw == null || raw.isBlank()) ? null : raw.strip();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) {
            throw new RowParseException("row has no date");
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new RowParseException("unrecognized date '" + raw + "'");
    }

    /**
     * Debit → negative, credit → positive; or a single signed Amount used as-is.
     * Zero/blank rows are treated as noise and skipped.
     */
    private BigDecimal parseAmount(CSVRecord record, Map<CsvColumn, Integer> columns) {
        BigDecimal amount;
        if (columns.containsKey(CsvColumn.DEBIT) || columns.containsKey(CsvColumn.CREDIT)) {
            BigDecimal debit = number(value(record, columns, CsvColumn.DEBIT));
            BigDecimal credit = number(value(record, columns, CsvColumn.CREDIT));
            if (debit == null && credit == null) {
                throw new RowParseException("row has neither a debit nor a credit");
            }
            BigDecimal out = debit == null ? BigDecimal.ZERO : debit.abs();
            BigDecimal in = credit == null ? BigDecimal.ZERO : credit.abs();
            amount = in.subtract(out);
        } else {
            amount = number(value(record, columns, CsvColumn.AMOUNT));
            if (amount == null) {
                throw new RowParseException("row has no amount");
            }
        }
        if (amount.signum() == 0) {
            throw new RowParseException("row has a zero amount");
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Parse money that may carry a currency symbol, thousands separators, or a
     * parenthesised negative. Returns {@code null} for blank; throws for junk.
     */
    private BigDecimal number(String raw) {
        if (raw == null) {
            return null;
        }
        boolean negative = raw.startsWith("(") && raw.endsWith(")");
        String cleaned = raw.replaceAll("[()\\s,$£€]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            throw new RowParseException("'" + raw + "' is not a valid amount");
        }
    }

    /** Internal signal that one row is unparseable; caught and recorded per row. */
    private static final class RowParseException extends RuntimeException {
        RowParseException(String message) {
            super(message);
        }
    }
}
