package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import java.io.Serial;

/** For example a transfer that would debit or credit one of the bank's own funding accounts. */
public final class AccountNotEligibleForPostingException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotEligibleForPostingException(AccountId accountId, PostingType postingType) {
        super(ErrorCategory.RULE_VIOLATION, "ACCOUNT_NOT_ELIGIBLE",
                "Account " + accountId + " cannot take part in a " + postingType + " posting");
    }
}
