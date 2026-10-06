package io.github.egeozdemirr.corebank.account.domain.event;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerOwner;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import java.time.Instant;
import java.util.Currency;

/** A customer account was opened. The TCKN is left out on purpose: downstream consumers do not need it. */
public record AccountOpened(
        AccountId accountId,
        Iban iban,
        CustomerId customerId,
        HolderName holderName,
        Currency currency,
        Instant openedAt) {

    public static AccountOpened of(Account account, CustomerOwner owner) {
        return new AccountOpened(account.id(), account.iban(), owner.customerId(), owner.holderName(),
                account.currency(), account.openedAt());
    }
}
