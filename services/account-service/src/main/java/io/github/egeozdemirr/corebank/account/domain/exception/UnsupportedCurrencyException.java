package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

public final class UnsupportedCurrencyException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_CODE = "UNSUPPORTED_CURRENCY";

    public UnsupportedCurrencyException(String currencyCode) {
        super(ErrorCategory.INVALID_INPUT, ERROR_CODE, "Currency is not supported: " + currencyCode);
    }

    public UnsupportedCurrencyException(String currencyCode, Throwable cause) {
        super(ErrorCategory.INVALID_INPUT, ERROR_CODE, "Currency is not supported: " + currencyCode, cause);
    }
}
