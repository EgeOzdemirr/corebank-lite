package io.github.egeozdemirr.corebank.transfer.domain.money;

import io.github.egeozdemirr.corebank.transfer.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidAmountException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in one currency. Always scale 2; rounding uses HALF_EVEN (banker's rounding) so that
 * repeated rounding does not drift in one direction.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(SCALE, ROUNDING_MODE);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /** Compares amounts of the same currency; amounts in different currencies are never comparable. */
    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public Money requirePositive() {
        if (!isPositive()) {
            throw new InvalidAmountException("Amount must be positive but was " + this);
        }
        return this;
    }

    public boolean hasCurrency(Currency expected) {
        return currency.equals(expected);
    }

    private void requireSameCurrency(Money other) {
        if (!other.hasCurrency(currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
