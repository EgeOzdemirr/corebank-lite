package io.github.egeozdemirr.corebank.account.domain.account;

/**
 * Kind of account. Whether a balance may go below zero is a property of the type, never of a particular account id:
 * customer accounts must be funded, while the bank's funding accounts are the contra side of deposits and therefore
 * carry a negative balance by design.
 */
public enum AccountType {
    CUSTOMER(false),
    FUNDING(true);

    private final boolean negativeBalanceAllowed;

    AccountType(boolean negativeBalanceAllowed) {
        this.negativeBalanceAllowed = negativeBalanceAllowed;
    }

    public boolean allowsNegativeBalance() {
        return negativeBalanceAllowed;
    }
}
