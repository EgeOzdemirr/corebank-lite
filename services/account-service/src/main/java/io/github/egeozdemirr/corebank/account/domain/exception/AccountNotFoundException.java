package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import java.io.Serial;

public final class AccountNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";

    public AccountNotFoundException(AccountId accountId) {
        super(ErrorCategory.NOT_FOUND, ERROR_CODE, "Account not found: " + accountId);
    }

    /** The message carries the masked IBAN only ({@link Iban#toString()}). */
    public AccountNotFoundException(Iban iban) {
        super(ErrorCategory.NOT_FOUND, ERROR_CODE, "Account not found: " + iban);
    }
}
