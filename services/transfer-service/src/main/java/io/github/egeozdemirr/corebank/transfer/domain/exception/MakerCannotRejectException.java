package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.io.Serial;

/** Segregation of duties also covers rejection: the maker cannot withdraw a transfer as if a checker had decided. */
public final class MakerCannotRejectException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MakerCannotRejectException(TransferId transferId) {
        super(ErrorCategory.RULE_VIOLATION, "MAKER_CANNOT_REJECT",
                "Transfer " + transferId + " must be rejected by a user other than its maker");
    }
}
