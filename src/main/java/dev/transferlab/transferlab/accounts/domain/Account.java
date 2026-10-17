package dev.transferlab.transferlab.accounts.domain;

import dev.transferlab.transferlab.shared.domain.Money;
import dev.transferlab.transferlab.shared.domain.BusinessRejection;

import java.util.Objects;
import java.util.UUID;

public record Account(
        UUID id,
        UUID ownerId,
        Money balance,
        Status status
) {

    public enum Status {
        ACTIVE,
        FROZEN
    }

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(status, "status");
    }

    public static Account open(UUID id, UUID ownerId) {
        return new Account(id, ownerId, Money.kzt(0), Status.ACTIVE);
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw new BusinessRejection(
                    BusinessRejection.Code.ACCOUNT_INACTIVE
            );
        }
    }

    public Account debit(Money amount) {
        Objects.requireNonNull(amount, "amount").requirePositive();
        requireActive();

        if (balance.amountMinor() < amount.amountMinor()) {
            throw new BusinessRejection(
                    BusinessRejection.Code.INSUFFICIENT_FUNDS
            );
        }


        return new Account(id, ownerId, balance.subtract(amount), status);
    }

    public Account credit(Money amount) {
        Objects.requireNonNull(amount, "amount").requirePositive();
        requireActive();

        return new Account(id, ownerId, balance.add(amount), status);
    }
}