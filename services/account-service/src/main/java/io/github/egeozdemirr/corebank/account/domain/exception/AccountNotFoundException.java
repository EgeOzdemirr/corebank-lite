package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import java.io.Serial;

public final class AccountNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(AccountId accountId) {
        super(ErrorCategory.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found: " + accountId);
    }
}
