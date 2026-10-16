package dev.transferlab.transferlab.transfers.application;

import dev.transferlab.transferlab.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

public record CreateTransferCommand(
        UUID actorId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        Money amount
) {

    public CreateTransferCommand {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        Objects.requireNonNull(amount, "amount").requirePositive();

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("SAME_ACCOUNT");
        }
    }
}
