package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import java.io.Serial;

public final class InsufficientFundsException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(AccountId accountId) {
        super(ErrorCategory.RULE_VIOLATION, "INSUFFICIENT_FUNDS", "Insufficient funds on account " + accountId);
    }
}
