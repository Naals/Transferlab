package dev.transferlab.transferlab.ledger.domain;

import dev.transferlab.transferlab.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

public record JournalEntry(
        UUID accountId,
        Direction direction,
        Money amount
) {

    public enum Direction {
        DEBIT,
        CREDIT
    }

    public JournalEntry {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount").requirePositive();
    }
}