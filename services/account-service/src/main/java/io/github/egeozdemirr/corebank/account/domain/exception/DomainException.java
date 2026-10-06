package io.github.egeozdemirr.corebank.account.domain.exception;

import java.io.Serial;

/**
 * Base type of every business rule failure.
 *
 * <p>Messages end up in logs and API responses, so they must never contain a TCKN or an unmasked IBAN.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCategory category;
    private final String errorCode;

    protected DomainException(ErrorCategory category, String errorCode, String message) {
        super(message);
        this.category = category;
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCategory category, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.errorCode = errorCode;
    }

    public ErrorCategory category() {
        return category;
    }

    /** Stable, machine-readable code that clients can branch on. */
    public String errorCode() {
        return errorCode;
    }
}
