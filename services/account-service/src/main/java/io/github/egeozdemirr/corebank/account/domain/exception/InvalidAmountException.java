package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

public final class InvalidAmountException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String reason) {
        super(ErrorCategory.INVALID_INPUT, "INVALID_AMOUNT", reason);
    }
}
