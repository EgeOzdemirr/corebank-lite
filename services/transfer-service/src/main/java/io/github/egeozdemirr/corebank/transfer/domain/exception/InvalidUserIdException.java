package io.github.egeozdemirr.corebank.transfer.domain.exception;

import java.io.Serial;

public final class InvalidUserIdException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidUserIdException() {
        super(ErrorCategory.INVALID_INPUT, "INVALID_USER_ID",
                "User id must be 1 to 64 letters, digits or the characters . _ : -");
    }
}
