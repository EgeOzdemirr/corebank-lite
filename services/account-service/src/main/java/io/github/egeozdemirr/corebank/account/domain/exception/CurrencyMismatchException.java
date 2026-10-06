package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;
import java.util.Currency;

public final class CurrencyMismatchException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super(ErrorCategory.INVALID_INPUT, "CURRENCY_MISMATCH",
                "Currency mismatch: expected " + expected.getCurrencyCode() + " but was " + actual.getCurrencyCode());
    }
}
