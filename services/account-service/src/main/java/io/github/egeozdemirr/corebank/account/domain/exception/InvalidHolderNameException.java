package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

public final class InvalidHolderNameException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidHolderNameException(String reason) {
        super(ErrorCategory.INVALID_INPUT, "INVALID_HOLDER_NAME", reason);
    }
}
