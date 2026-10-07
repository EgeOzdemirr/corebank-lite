package io.github.egeozdemirr.corebank.transfer.domain.account;

import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import java.util.Currency;
import java.util.Objects;

/** One side of a transfer as account-service reported it when the transfer was requested. */
public record AccountReference(AccountId accountId, Iban iban, Currency currency) {

    public AccountReference {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(iban, "iban");
        Objects.requireNonNull(currency, "currency");
    }
}
