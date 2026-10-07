package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.io.Serial;

/** The approval window (the business day of the request) is over; the transfer can only expire now. */
public final class ApprovalExpiredException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ApprovalExpiredException(TransferId transferId) {
        super(ErrorCategory.CONFLICT, "APPROVAL_EXPIRED",
                "Transfer " + transferId + " was not approved within the business day of its request");
    }
}
