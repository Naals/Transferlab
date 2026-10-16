package dev.transferlab.transferlab.transfers.integration;


import dev.transferlab.transferlab.accounts.application.port.AccountRepository;
import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.shared.domain.Money;
import dev.transferlab.transferlab.support.AccountFixtures;
import dev.transferlab.transferlab.support.PostgreSqlTestConfiguration;
import dev.transferlab.transferlab.transfers.application.CreateTransferCommand;
import dev.transferlab.transferlab.transfers.application.TransferTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(PostgreSqlTestConfiguration.class)
class TransferAtomicityIT {

    @Autowired AccountRepository accounts;
    @Autowired TransferTransactionService transfers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private AccountFixtures fixtures;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        fixtures = new AccountFixtures(accounts, jdbc, transaction);
    }

    @Test
    void postsBalancesAndBalancedJournalTogether() {
        Account source = fixtures.customer(1_000_000);
        Account destination = fixtures.customer(0);

        var result = transfers.execute(command(source, destination, 250_000));

        assertThat(balance(source)).isEqualTo(750_000);
        assertThat(balance(destination)).isEqualTo(250_000);

        Long entryCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM journal_entries
                WHERE journal_id = ?
                """,
                Long.class,
                result.journal().id()
        );

        Long net = jdbc.queryForObject("""
                SELECT SUM(
                    CASE
                        WHEN direction = 'DEBIT' THEN amount_minor
                        ELSE -amount_minor
                    END
                )
                FROM journal_entries
                WHERE journal_id = ?
                """,
                Long.class,
                result.journal().id()
        );

        assertThat(entryCount).isEqualTo(2L);
        assertThat(net).isZero();
    }

    @Test
    void insufficientFundsLeavesNoTransfer() {
        Account source = fixtures.customer(100);
        Account destination = fixtures.customer(0);

        assertThatIllegalStateException()
                .isThrownBy(
                        () -> transfers.execute(command(source, destination, 101))
                )
                .withMessage("INSUFFICIENT_FUNDS");

        assertThat(balance(source)).isEqualTo(100);
        assertThat(balance(destination)).isZero();
        assertThat(transferCount(source.id())).isZero();
    }

    @Test
    void rollbackAfterPostingRemovesAllTransferEffects() {
        Account source = fixtures.customer(1_000);
        Account destination = fixtures.customer(0);

        assertThatIllegalStateException().isThrownBy(
                () -> transaction.executeWithoutResult(status -> {
                    transfers.execute(command(source, destination, 400));

                    // Fail after all posting writes, before the outer commit.
                    throw new IllegalStateException("SIMULATED_FAILURE");
                })
        ).withMessage("SIMULATED_FAILURE");

        assertThat(balance(source)).isEqualTo(1_000);
        assertThat(balance(destination)).isZero();
        assertThat(transferCount(source.id())).isZero();

        Long destinationEntries = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM journal_entries
                WHERE account_id = ?
                """,
                Long.class,
                destination.id()
        );

        assertThat(destinationEntries).isZero();
    }

    @Test
    void databaseRejectsUnbalancedJournalAtCommit() {
        Account source = fixtures.customer(100);
        UUID journalId = UUID.randomUUID();

        assertThatThrownBy(
                () -> transaction.executeWithoutResult(status -> {
                    jdbc.update("""
                            INSERT INTO journal_transactions (
                                id, transfer_id, kind, posted_at
                            )
                            VALUES (?, NULL, 'FUNDING', CURRENT_TIMESTAMP)
                            """,
                            journalId
                    );

                    jdbc.update("""
                            INSERT INTO journal_entries (
                                journal_id, account_id, direction, amount_minor
                            )
                            VALUES (?, ?, 'CREDIT', 100)
                            """,
                            journalId,
                            source.id()
                    );
                })
        ).isInstanceOf(RuntimeException.class);

        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM journal_transactions
                WHERE id = ?
                """,
                Long.class,
                journalId
        );

        assertThat(count).isZero();
    }

    private CreateTransferCommand command(
            Account source,
            Account destination,
            long amount
    ) {
        return new CreateTransferCommand(
                source.ownerId(),
                source.id(),
                destination.id(),
                Money.kzt(amount)
        );
    }

    private long balance(Account account) {
        return accounts.findById(account.id())
                .orElseThrow()
                .balance()
                .amountMinor();
    }

    private long transferCount(UUID sourceId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM transfers
                WHERE source_account_id = ?
                """,
                Long.class,
                sourceId
        );
    }
}
