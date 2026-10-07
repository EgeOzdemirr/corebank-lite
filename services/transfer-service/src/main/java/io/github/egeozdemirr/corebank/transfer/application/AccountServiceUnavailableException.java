package io.github.egeozdemirr.corebank.transfer.application;

import java.io.Serial;

/**
 * account-service did not give a usable answer to an account lookup. Nothing was recorded yet, so the client may
 * simply try again (HTTP 503).
 */
public final class AccountServiceUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
