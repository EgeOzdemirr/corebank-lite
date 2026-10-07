package io.github.egeozdemirr.corebank.account.domain.exception;

import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import java.io.Serial;

/** A caller reused a posting id for a different movement; the recorded posting is left untouched. */
public final class PostingConflictException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PostingConflictException(PostingId postingId) {
        super(ErrorCategory.CONFLICT, "POSTING_ID_CONFLICT",
                "Posting " + postingId + " was already recorded with different accounts or amount");
    }
}
