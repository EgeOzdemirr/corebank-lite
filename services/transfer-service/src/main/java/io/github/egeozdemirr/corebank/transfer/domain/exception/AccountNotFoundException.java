package io.github.egeozdemirr.corebank.transfer.domain.exception;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import java.io.Serial;

/** account-service knows no customer account with this id or IBAN; the bank's own accounts count as unknown. */
public final class AccountNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";

    public AccountNotFoundException(AccountId accountId) {
        super(ErrorCategory.NOT_FOUND, ERROR_CODE, "Account not found: " + accountId);
    }

    /** The message carries the masked IBAN only ({@link Iban#toString()}). */
    public AccountNotFoundException(Iban iban) {
        super(ErrorCategory.NOT_FOUND, ERROR_CODE, "Account not found: " + iban);
    }
}
