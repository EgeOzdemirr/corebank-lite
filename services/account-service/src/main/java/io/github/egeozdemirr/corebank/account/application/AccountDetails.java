package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.Objects;

/** An account together with its current balance, whether materialised or derived from the ledger. */
public record AccountDetails(Account account, Money balance) {

    public AccountDetails {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(balance, "balance");
    }
}
