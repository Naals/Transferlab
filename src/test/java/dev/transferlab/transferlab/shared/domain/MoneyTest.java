package dev.transferlab.transferlab.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void calculatesExactMinorUnits() {
        Money result = Money.kzt(10).add(Money.kzt(20));

        assertThat(result).isEqualTo(Money.kzt(30));
    }

    @Test
    void rejectsNegativeAmountsAndUnsupportedCurrencies() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.kzt(-1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(100, "USD"));
    }

    @Test
    void rejectsOverflow() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(
                        () -> Money.kzt(Long.MAX_VALUE).add(Money.kzt(1))
                );
    }

    @Test
    void rejectsNegativeSubtractionResult() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.kzt(100).subtract(Money.kzt(101)));
    }

    @Test
    void zeroIsValidForBalancesButNotPayments() {
        assertThat(Money.kzt(0).amountMinor()).isZero();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.kzt(0).requirePositive());
    }
}