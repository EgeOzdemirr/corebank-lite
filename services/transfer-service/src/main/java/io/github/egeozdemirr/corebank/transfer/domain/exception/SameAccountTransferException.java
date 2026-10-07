package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import java.io.Serial;

public final class SameAccountTransferException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SameAccountTransferException(AccountId accountId) {
        super(ErrorCategory.INVALID_INPUT, "SAME_ACCOUNT_TRANSFER",
                "Source and target are the same account " + accountId);
    }
}
