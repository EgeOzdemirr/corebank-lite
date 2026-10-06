package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

public final class InvalidIbanException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidIbanException() {
        super(ErrorCategory.INVALID_INPUT, "INVALID_IBAN", "IBAN failed Turkish format or check digit validation");
    }
}
