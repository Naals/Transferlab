package dev.transferlab.transferlab.transfers.infrastructure.persistence;

import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.ledger.domain.JournalEntry;
import dev.transferlab.transferlab.transfers.application.port.TransferPostingPort;
import dev.transferlab.transferlab.transfers.domain.TransferPlanner.TransferPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Repository
public class JdbcTransferPostingAdapter implements TransferPostingPort {

    private final JdbcTemplate jdbc;

    public JdbcTransferPostingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(TransferPlan plan) {
        var journal = plan.journal();

        validateInternalTransfer(plan);

        long amountMinor = journal.entries().getFirst().amount().amountMinor();

        jdbc.update("""
                INSERT INTO transfers (
                    id, source_account_id, destination_account_id,
                    amount_minor, currency, posted_at
                )
                VALUES (?, ?, ?, ?, 'KZT', ?)
                """,
                journal.transferId(),
                plan.sourceAfter().id(),
                plan.destinationAfter().id(),
                amountMinor,
                Timestamp.from(journal.postedAt())
        );

        jdbc.update("""
                INSERT INTO journal_transactions (
                    id, transfer_id, kind, posted_at
                )
                VALUES (?, ?, 'TRANSFER', ?)
                """,
                journal.id(),
                journal.transferId(),
                Timestamp.from(journal.postedAt())
        );

        for (JournalEntry entry : journal.entries()) {
            jdbc.update("""
                    INSERT INTO journal_entries (
                        journal_id, account_id, direction, amount_minor
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    journal.id(),
                    entry.accountId(),
                    entry.direction().name(),
                    entry.amount().amountMinor()
            );
        }

        updateBalance(plan.sourceAfter());
        updateBalance(plan.destinationAfter());
    }

    private void updateBalance(Account account) {
        int updated = jdbc.update("""
                UPDATE accounts
                SET balance_minor = ?
                WHERE id = ?
                  AND kind = 'CUSTOMER'
                """,
                account.balance().amountMinor(),
                account.id()
        );

        if (updated != 1) {
            throw new IllegalStateException("ACCOUNT_UPDATE_FAILED");
        }
    }

    private void validateInternalTransfer(TransferPlan plan) {
        var entries = plan.journal().entries();

        if (entries.size() != 2) {
            throw new IllegalArgumentException(
                    "Internal transfer requires exactly two entries"
            );
        }

        var debit = entries.get(0);
        var credit = entries.get(1);

        boolean valid =
                debit.direction() == JournalEntry.Direction.DEBIT
                        && credit.direction() == JournalEntry.Direction.CREDIT
                        && debit.accountId().equals(plan.sourceAfter().id())
                        && credit.accountId().equals(plan.destinationAfter().id())
                        && debit.amount().equals(credit.amount());

        if (!valid) {
            throw new IllegalArgumentException("INVALID_TRANSFER_POSTING");
        }
    }
}
