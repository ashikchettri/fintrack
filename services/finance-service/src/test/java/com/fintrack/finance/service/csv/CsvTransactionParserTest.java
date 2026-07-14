package com.fintrack.finance.service.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CsvTransactionParserTest {

    private final CsvTransactionParser parser = new CsvTransactionParser();

    private static InputStream csv(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void debitIsNegativeAndCreditIsPositive() {
        CsvParseResult result = parser.parse(csv("""
                Transaction Date,Details,Debit,Credit
                "05 Jan 2026","Coffee","4.50",""
                "06 Jan 2026","Refund","","10.00"
                """));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("-4.50");   // debit → spend
        assertThat(result.rows().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(result.rows().get(1).amount()).isEqualByComparingTo("10.00");   // credit → income
    }

    @Test
    void mapsFlexibleHeadersAndASingleSignedAmountColumn() {
        // different bank: "Date"/"Description"/"Amount" instead of Details/Debit/Credit
        CsvParseResult result = parser.parse(csv("""
                Date,Description,Amount
                2026-02-01,Salary,3000.00
                2026-02-02,Rent,-1500.00
                """));

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).description()).isEqualTo("Salary");
        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("3000.00");
        assertThat(result.rows().get(1).amount()).isEqualByComparingTo("-1500.00");
    }

    @Test
    void handlesCurrencySymbolsThousandsAndParenthesisedNegatives() {
        CsvParseResult result = parser.parse(csv("""
                Date,Description,Amount
                2026-03-01,Bonus,"$1,234.50"
                2026-03-02,Chargeback,(4.50)
                """));

        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("1234.50");
        assertThat(result.rows().get(1).amount()).isEqualByComparingTo("-4.50");
    }

    @Test
    void skipsUnparseableRowsAndReportsThem() {
        CsvParseResult result = parser.parse(csv("""
                Date,Description,Amount
                2026-04-01,Good,10.00
                not-a-date,Bad date,10.00
                2026-04-03,Bad amount,abc
                2026-04-04,Zero,0.00
                """));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.errors()).hasSize(3);   // bad date, bad amount, zero amount
        assertThat(result.errors()).allSatisfy(e -> assertThat(e.rowNumber()).isPositive());
    }

    @Test
    void missingRequiredColumnsFailsTheWholeFile() {
        // no amount/debit/credit column at all
        assertThatExceptionOfType(CsvImportException.class)
                .isThrownBy(() -> parser.parse(csv("""
                        Date,Description
                        2026-01-01,Nope
                        """)))
                .withMessageContaining("date");
    }

    @Test
    void aFileWithNoDataRowsFails() {
        assertThatExceptionOfType(CsvImportException.class)
                .isThrownBy(() -> parser.parse(csv("Date,Description,Amount\n")));
    }

    @Test
    void fallsBackToOriginalDescriptionWhenDetailsBlank() {
        CsvParseResult result = parser.parse(csv("""
                Transaction Date,Details,Amount,Original Description
                2026-05-01,,-9.99,"RAW BANK TEXT"
                """));

        assertThat(result.rows().get(0).description()).isEqualTo("RAW BANK TEXT");
    }
}
