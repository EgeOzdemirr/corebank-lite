package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.io.Serial;

/** Segregation of duties: the user who created a transfer above the approval threshold cannot approve it. */
public final class MakerCannotApproveException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MakerCannotApproveException(TransferId transferId) {
        super(ErrorCategory.RULE_VIOLATION, "MAKER_CANNOT_APPROVE",
                "Transfer " + transferId + " must be approved by a user other than its maker");
    }
}
