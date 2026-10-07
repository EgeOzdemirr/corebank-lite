package io.github.egeozdemirr.corebank.account.domain.account;

/**
 * Kind of account. Balance rules are properties of the type, never of a particular account id.
 *
 * <p>Customer accounts must be funded, so their balance is materialised on the account row and checked on every
 * posting. The bank's funding accounts are the contra side of every deposit: they may go negative, and their
 * balance is derived from the ledger instead of being stored, so that concurrent postings never contend for their
 * row (ADR-0003).
 */
public enum AccountType {
    CUSTOMER(false, true),
    FUNDING(true, false);

    private final boolean negativeBalanceAllowed;
    private final boolean balanceMaterialised;

    AccountType(boolean negativeBalanceAllowed, boolean balanceMaterialised) {
        this.negativeBalanceAllowed = negativeBalanceAllowed;
        this.balanceMaterialised = balanceMaterialised;
    }

    public boolean allowsNegativeBalance() {
        return negativeBalanceAllowed;
    }

    /** True if the balance is stored on the account; false if it is only derived from the ledger. */
    public boolean materialisesBalance() {
        return balanceMaterialised;
    }
}
