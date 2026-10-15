package dev.transferlab.transferlab.transfers.domain;

import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.ledger.domain.JournalEntry;
import dev.transferlab.transferlab.ledger.domain.JournalTransaction;
import dev.transferlab.transferlab.shared.domain.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TransferPlanner {

    public TransferPlan plan(
            UUID transferId,
            UUID journalId,
            UUID actorId,
            Account source,
            Account destination,
            Money amount,
            Instant postedAt
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(amount, "amount");

        if (!source.ownerId().equals(actorId)) {
            throw new SecurityException("SOURCE_NOT_OWNED");
        }

        if (source.id().equals(destination.id())) {
            throw new IllegalArgumentException("SAME_ACCOUNT");
        }

        amount.requirePositive();
        source.requireActive();
        destination.requireActive();

        Account sourceAfter = source.debit(amount);
        Account destinationAfter = destination.credit(amount);

        JournalTransaction journal = new JournalTransaction(
                journalId,
                transferId,
                postedAt,
                List.of(
                        new JournalEntry(
                                source.id(),
                                JournalEntry.Direction.DEBIT,
                                amount
                        ),
                        new JournalEntry(
                                destination.id(),
                                JournalEntry.Direction.CREDIT,
                                amount
                        )
                )
        );

        return new TransferPlan(sourceAfter, destinationAfter, journal);
    }

    public record TransferPlan(
            Account sourceAfter,
            Account destinationAfter,
            JournalTransaction journal
    ) {
    }
}