package io.github.egeozdemirr.corebank.account.domain.account;

import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import java.util.Objects;

/** A customer's account. {@code toString()} is safe to log: {@link Tckn} masks itself. */
public record CustomerOwner(CustomerId customerId, HolderName holderName, Tckn tckn) implements AccountOwner {

    public CustomerOwner {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(tckn, "tckn");
    }

    @Override
    public AccountType accountType() {
        return AccountType.CUSTOMER;
    }
}
