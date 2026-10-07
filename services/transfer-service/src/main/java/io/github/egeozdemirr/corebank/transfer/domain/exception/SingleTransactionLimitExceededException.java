package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import java.io.Serial;

public final class SingleTransactionLimitExceededException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SingleTransactionLimitExceededException(Money amount, Money limit) {
        super(ErrorCategory.RULE_VIOLATION, "SINGLE_TRANSACTION_LIMIT_EXCEEDED",
                "Amount " + amount + " exceeds the single transaction limit of " + limit);
    }
}
