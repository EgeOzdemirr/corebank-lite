package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.io.Serial;

public final class UnbalancedPostingException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnbalancedPostingException(PostingId postingId, Money imbalance) {
        super(ErrorCategory.RULE_VIOLATION, "UNBALANCED_POSTING",
                "Posting " + postingId + " does not balance; debits and credits differ by " + imbalance);
    }
}
