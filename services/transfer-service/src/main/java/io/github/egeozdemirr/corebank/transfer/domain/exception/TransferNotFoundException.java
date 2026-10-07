package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.io.Serial;

public final class TransferNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TransferNotFoundException(TransferId transferId) {
        super(ErrorCategory.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer not found: " + transferId);
    }
}
