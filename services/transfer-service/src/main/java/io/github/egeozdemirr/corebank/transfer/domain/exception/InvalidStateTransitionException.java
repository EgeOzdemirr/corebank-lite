package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import java.io.Serial;

/** The state machine does not allow this step from the transfer's current status. */
public final class InvalidStateTransitionException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidStateTransitionException(TransferId transferId, TransferStatus from, TransferStatus to) {
        super(ErrorCategory.CONFLICT, "INVALID_STATE_TRANSITION",
                "Transfer " + transferId + " cannot move from " + from + " to " + to);
    }
}
