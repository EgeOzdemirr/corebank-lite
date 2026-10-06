package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

public final class InvalidTcknException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTcknException() {
        super(ErrorCategory.INVALID_INPUT, "INVALID_TCKN", "TCKN failed format or check digit validation");
    }
}
