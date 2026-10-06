package io.github.egeozdemirr.corebank.account.domain.ledger;

import io.github.egeozdemirr.corebank.account.domain.money.Money;

/**
 * Side of a ledger line, seen from the bank's books. Customer deposits are liabilities of the bank, so a credit
 * increases an account's balance and a debit decreases it. The signed amounts of a balanced posting sum to zero.
 */
public enum EntryDirection {
    DEBIT,
    CREDIT;

    public Money signed(Money amount) {
        return this == DEBIT ? amount.negate() : amount;
    }
}
