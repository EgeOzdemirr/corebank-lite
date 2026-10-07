package io.github.egeozdemirr.corebank.transfer.domain.exception;

import java.io.Serial;

public final class InvalidBeneficiaryNameException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidBeneficiaryNameException(String reason) {
        super(ErrorCategory.INVALID_INPUT, "INVALID_BENEFICIARY_NAME", reason);
    }
}
