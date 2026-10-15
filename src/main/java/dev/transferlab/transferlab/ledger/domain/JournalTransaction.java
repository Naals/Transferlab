package dev.transferlab.transferlab.ledger.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record JournalTransaction(
        UUID id,
        UUID transferId,
        Instant postedAt,
        List<JournalEntry> entries
) {

    public JournalTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(postedAt, "postedAt");

        entries = List.copyOf(entries);

        if (entries.size() < 2) {
            throw new IllegalArgumentException(
                    "A journal transaction requires at least two entries"
            );
        }

        long debits = 0;
        long credits = 0;

        for (JournalEntry entry : entries) {
            if (entry.direction() == JournalEntry.Direction.DEBIT) {
                debits = Math.addExact(debits, entry.amount().amountMinor());
            } else {
                credits = Math.addExact(credits, entry.amount().amountMinor());
            }
        }

        if (debits != credits) {
            throw new IllegalArgumentException(
                    "Total debits must equal total credits"
            );
        }
    }
}