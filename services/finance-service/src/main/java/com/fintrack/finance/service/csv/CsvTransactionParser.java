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

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {

            Map<CsvColumn, Integer> columns = mapColumns(parser.getHeaderNames());
            requireUsableColumns(columns, parser.getHeaderNames());

            for (CSVRecord record : parser) {
                try {
                    rows.add(toTransaction(record, columns));
                } catch (RowParseException e) {
                    errors.add(new CsvParseResult.RowError((int) record.getRecordNumber(), e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new CsvImportException("Could not read the CSV file: " + e.getMessage(), e);
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new CsvImportException("The CSV file has no data rows.");
        }
        return new CsvParseResult(rows, errors);
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
