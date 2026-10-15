package dev.transferlab.transferlab.ledger.domain;

import dev.transferlab.transferlab.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.transferlab.transferlab.ledger.domain.JournalEntry.Direction.*;
import static org.assertj.core.api.Assertions.*;

class JournalTransactionTest {

    @Test
    void acceptsBalancedSplitEntriesAndProtectsTheirImmutability() {
        var entries = new ArrayList<>(List.of(
                entry(DEBIT, 100),
                entry(CREDIT, 60),
                entry(CREDIT, 40)
        ));

        JournalTransaction transaction = journal(entries);
        entries.clear();

        assertThat(transaction.entries()).hasSize(3);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> transaction.entries().clear());
    }

    @Test
    void rejectsUnbalancedEntries() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> journal(List.of(
                        entry(DEBIT, 100),
                        entry(CREDIT, 99)
                ))
        );
    }

    @Test
    void rejectsOneSidedTransaction() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> journal(List.of(entry(DEBIT, 100)))
        );
    }

    @Test
    void rejectsOverflowWhenAddingEntries() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(
                () -> journal(List.of(
                        entry(DEBIT, Long.MAX_VALUE),
                        entry(DEBIT, 1),
                        entry(CREDIT, 1)
                ))
        );
    }

    private JournalEntry entry(
            JournalEntry.Direction direction,
            long amount
    ) {
        return new JournalEntry(
                UUID.randomUUID(),
                direction,
                Money.kzt(amount)
        );
    }

    private JournalTransaction journal(List<JournalEntry> entries) {
        return new JournalTransaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.EPOCH,
                entries
        );
    }
}