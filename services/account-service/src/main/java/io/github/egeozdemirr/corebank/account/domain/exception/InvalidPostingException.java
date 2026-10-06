package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

public final class InvalidPostingException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPostingException(String reason) {
        super(ErrorCategory.RULE_VIOLATION, "INVALID_POSTING", reason);
    }
}
