package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import java.io.Serial;

public final class AccountNotActiveException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotActiveException(AccountId accountId) {
        super(ErrorCategory.RULE_VIOLATION, "ACCOUNT_NOT_ACTIVE", "Account is not active: " + accountId);
    }
}
