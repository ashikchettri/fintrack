package com.fintrack.finance.service;

import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.csv.CsvParseResult;
import com.fintrack.finance.service.csv.CsvTransactionParser;
import com.fintrack.finance.service.csv.ParsedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock
    private CsvTransactionParser parser;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private TransactionImportService service;
    private AuthenticatedMember caller;

    private static final InputStream ANY_CSV = new ByteArrayInputStream(new byte[0]);

    @BeforeEach
    void setUp() {
        service = new TransactionImportService(parser, accountRepository, transactionRepository);
        caller = new AuthenticatedMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER");
    }

    private static ParsedTransaction row(int n, String account, LocalDate date, String amount, String original) {
        return new ParsedTransaction(n, date, account, "Reddy Express",
                "Transportation", "Fuel", null, null, new BigDecimal(amount), original);
    }

    @Test
    void importsRowsCreatesAccountsAndKeepsGenuineDuplicatesWithinAFile() {
        // two byte-for-byte identical rows (two real fuel stops the same day) + a distinct one
        LocalDate day = LocalDate.of(2026, 5, 26);
        when(parser.parse(any())).thenReturn(new CsvParseResult(List.of(
                row(2, "Everyday", day, "-60.00", "Reddy Express 6701"),
                row(3, "Everyday", day, "-60.00", "Reddy Express 6701"),
                row(4, "Everyday", LocalDate.of(2026, 7, 11), "-12.50", "TfNSW Opal")),
                List.of()));
        when(transactionRepository.findDedupHashes(any(), any())).thenReturn(Set.of());
        when(accountRepository.findByHouseholdIdAndMemberIdAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ImportSummary summary = service.importCsv(caller, "statement.csv", "aud", ANY_CSV);

        assertThat(summary.imported()).isEqualTo(3);              // both duplicates kept on first import
        assertThat(summary.duplicatesSkipped()).isZero();
        assertThat(summary.accountsCreated()).containsExactly("Everyday");   // one account, resolved once
        assertThat(summary.currency()).isEqualTo("AUD");
        assertThat(summary.totalExpenses()).isEqualByComparingTo("132.50");

        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.captor();
        org.mockito.Mockito.verify(transactionRepository).saveAll(saved.capture());
        List<Transaction> txns = saved.getValue();
        assertThat(txns).hasSize(3);
        // the two identical rows get distinct digests, so neither is lost
        assertThat(txns.get(0).getDedupHash()).isNotEqualTo(txns.get(1).getDedupHash());
        // every imported row is personal + CSV-sourced (ADR 001)
        assertThat(txns).allSatisfy(t -> {
            assertThat(t.getVisibility()).isEqualTo(Visibility.PERSONAL);
            assertThat(t.getSource()).isEqualTo(TransactionSource.CSV_IMPORT);
        });
    }

    @Test
    void reimportingAnAlreadySeenRowIsSkippedAsDuplicate() {
        Account everyday = new Account(caller.householdId(), caller.memberId(),
                "Everyday", AccountType.OTHER, "AUD", BigDecimal.ZERO);
        when(accountRepository.findByHouseholdIdAndMemberIdAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.of(everyday));   // account already exists → never created
        when(parser.parse(any())).thenReturn(new CsvParseResult(List.of(
                row(2, "Everyday", LocalDate.of(2026, 7, 11), "-12.50", "TfNSW Opal")), List.of()));
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // first import: nothing seen yet → the row lands, and we learn its digest
        when(transactionRepository.findDedupHashes(any(), any())).thenReturn(Set.of());
        ImportSummary first = service.importCsv(caller, "statement.csv", "AUD", ANY_CSV);
        assertThat(first.imported()).isEqualTo(1);
        assertThat(first.accountsCreated()).isEmpty();

        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.captor();
        org.mockito.Mockito.verify(transactionRepository).saveAll(saved.capture());
        String priorHash = saved.getValue().get(0).getDedupHash();

        // second import of the same statement: that digest is now known → skipped
        when(transactionRepository.findDedupHashes(any(), any())).thenReturn(Set.of(priorHash));
        ImportSummary second = service.importCsv(caller, "statement.csv", "AUD", ANY_CSV);
        assertThat(second.imported()).isZero();
        assertThat(second.duplicatesSkipped()).isEqualTo(1);
    }

    @Test
    void rowsWithoutAnAccountColumnLandInADefaultAccount() {
        when(parser.parse(any())).thenReturn(new CsvParseResult(List.of(
                row(2, null, LocalDate.of(2026, 7, 11), "-12.50", "TfNSW Opal")), List.of()));
        when(transactionRepository.findDedupHashes(any(), any())).thenReturn(Set.of());
        when(accountRepository.findByHouseholdIdAndMemberIdAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ImportSummary summary = service.importCsv(caller, "statement.csv", "AUD", ANY_CSV);

        assertThat(summary.accountsCreated())
                .containsExactly(TransactionImportService.DEFAULT_ACCOUNT_NAME);
    }
}
