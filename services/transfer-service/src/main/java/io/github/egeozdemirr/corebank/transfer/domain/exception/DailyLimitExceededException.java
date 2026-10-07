package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import java.io.Serial;

/** The source account's transfers of the business day would exceed the daily limit of the currency. */
public final class DailyLimitExceededException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DailyLimitExceededException(Money amount, Money limit) {
        super(ErrorCategory.RULE_VIOLATION, "DAILY_LIMIT_EXCEEDED",
                "Amount " + amount + " would exceed the daily limit of " + limit);
    }
}
