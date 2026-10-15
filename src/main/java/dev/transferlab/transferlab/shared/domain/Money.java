package dev.transferlab.transferlab.shared.domain;

import java.util.Objects;

public record Money(long amountMinor, String currency) {

    public Money {
        if (amountMinor < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        if (!"KZT".equals(currency)) {
            throw new IllegalArgumentException("Only KZT is supported");
        }
    }

    public static Money kzt(long amountMinor) {
        return new Money(amountMinor, "KZT");
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other");

        return kzt(Math.addExact(amountMinor, other.amountMinor));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other");

        return kzt(Math.subtractExact(amountMinor, other.amountMinor));
    }

    public void requirePositive() {
        if (amountMinor == 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}