package dev.transferlab.transferlab.support;

import dev.transferlab.transferlab.accounts.application.port.AccountRepository;
import dev.transferlab.transferlab.accounts.domain.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

public final class AccountFixtures {

    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public AccountFixtures(
            AccountRepository accounts,
            JdbcTemplate jdbc,
            TransactionTemplate transactions
    ) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public Account customer(long initialBalanceMinor) {
        if (initialBalanceMinor < 0) {
            throw new IllegalArgumentException("Negative fixture balance");
        }

        UUID accountId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            accounts.insert(Account.open(accountId, ownerId));

            if (initialBalanceMinor > 0) {
                fund(accountId, initialBalanceMinor);
            }
        });

        return accounts.findById(accountId).orElseThrow();
    }

    private void fund(UUID customerId, long amountMinor) {
        UUID systemId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO accounts (
                    id, owner_id, kind, currency, balance_minor, status
                )
                VALUES (?, NULL, 'SYSTEM', 'KZT', ?, 'ACTIVE')
                """,
                systemId,
                -amountMinor
        );

        jdbc.update("""
                UPDATE accounts
                SET balance_minor = ?
                WHERE id = ?
                """,
                amountMinor,
                customerId
        );

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
                VALUES
                    (?, ?, 'DEBIT', ?),
                    (?, ?, 'CREDIT', ?)
                """,
                journalId, systemId, amountMinor,
                journalId, customerId, amountMinor
        );
    }
}
