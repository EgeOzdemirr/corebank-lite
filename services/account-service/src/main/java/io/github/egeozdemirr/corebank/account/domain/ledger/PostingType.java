package io.github.egeozdemirr.corebank.account.domain.ledger;

/** Business reason of a posting; kept on every ledger line so the ledger can be audited without other tables. */
public enum PostingType {
    /** Opening deposit of a new customer account, funded by the currency's funding account. */
    OPENING_DEPOSIT(false),
    /** Money moved between two customer accounts on behalf of transfer-service. */
    TRANSFER(true);

    private final boolean customerAccountsOnly;

    PostingType(boolean customerAccountsOnly) {
        this.customerAccountsOnly = customerAccountsOnly;
    }

    /** True if every line must belong to a customer account; the bank's own accounts are then not allowed. */
    public boolean customerAccountsOnly() {
        return customerAccountsOnly;
    }
}
