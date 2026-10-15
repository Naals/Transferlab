package dev.transferlab.transferlab.transfers.domain;

import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static dev.transferlab.transferlab.ledger.domain.JournalEntry.Direction.*;
import static org.assertj.core.api.Assertions.*;

class TransferPlannerTest {

    private final TransferPlanner planner = new TransferPlanner();
    private final UUID owner = UUID.randomUUID();

    private final Account source = new Account(
            UUID.randomUUID(),
            owner,
            Money.kzt(1_000_000),
            Account.Status.ACTIVE
    );

    private final Account destination = Account.open(
            UUID.randomUUID(),
            UUID.randomUUID()
    );

    @Test
    void transfers2500KztAndConservesTotalFunds() {
        var result = plan(owner, destination, 250_000);

        assertThat(result.sourceAfter().balance())
                .isEqualTo(Money.kzt(750_000));

        assertThat(result.destinationAfter().balance())
                .isEqualTo(Money.kzt(250_000));

        Money totalAfter = result.sourceAfter().balance()
                .add(result.destinationAfter().balance());

        assertThat(totalAfter)
                .isEqualTo(source.balance().add(destination.balance()));

        assertThat(result.journal().entries()).satisfiesExactly(
                debit -> {
                    assertThat(debit.accountId()).isEqualTo(source.id());
                    assertThat(debit.direction()).isEqualTo(DEBIT);
                    assertThat(debit.amount()).isEqualTo(Money.kzt(250_000));
                },
                credit -> {
                    assertThat(credit.accountId()).isEqualTo(destination.id());
                    assertThat(credit.direction()).isEqualTo(CREDIT);
                    assertThat(credit.amount()).isEqualTo(Money.kzt(250_000));
                }
        );
    }

    @Test
    void rejectsInsufficientFunds() {
        assertThatIllegalStateException()
                .isThrownBy(() -> plan(owner, destination, 1_000_001))
                .withMessage("INSUFFICIENT_FUNDS");
    }

    @Test
    void rejectsUnauthorizedSourceAccount() {
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(
                        () -> plan(UUID.randomUUID(), destination, 100)
                )
                .withMessage("SOURCE_NOT_OWNED");
    }

    @Test
    void rejectsSelfTransfer() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> plan(owner, source, 100))
                .withMessage("SAME_ACCOUNT");
    }

    @Test
    void rejectsZeroTransfer() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> plan(owner, destination, 0));
    }

    @Test
    void rejectsFrozenDestination() {
        Account frozen = new Account(
                destination.id(),
                destination.ownerId(),
                destination.balance(),
                Account.Status.FROZEN
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> plan(owner, frozen, 100))
                .withMessage("ACCOUNT_INACTIVE");
    }

    @Test
    void failedCreditDoesNotMutateSource() {
        Account full = new Account(
                destination.id(),
                destination.ownerId(),
                Money.kzt(Long.MAX_VALUE),
                Account.Status.ACTIVE
        );

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> plan(owner, full, 1));

        assertThat(source.balance()).isEqualTo(Money.kzt(1_000_000));
        assertThat(full.balance()).isEqualTo(Money.kzt(Long.MAX_VALUE));
    }

    private TransferPlanner.TransferPlan plan(
            UUID actor,
            Account target,
            long amountMinor
    ) {
        return planner.plan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                actor,
                source,
                target,
                Money.kzt(amountMinor),
                Instant.EPOCH
        );
    }
}