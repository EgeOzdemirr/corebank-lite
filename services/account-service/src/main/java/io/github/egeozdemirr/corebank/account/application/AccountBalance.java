package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.Objects;

public record AccountBalance(AccountId accountId, Iban iban, Money balance) {

    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(iban, "iban");
        Objects.requireNonNull(balance, "balance");
    }
}
