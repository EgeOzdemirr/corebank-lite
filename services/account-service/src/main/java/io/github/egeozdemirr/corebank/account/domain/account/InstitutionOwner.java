package io.github.egeozdemirr.corebank.account.domain.account;

import java.util.Objects;

/** An account the bank holds for itself, such as the funding account of a currency. */
public record InstitutionOwner(HolderName holderName) implements AccountOwner {

    public InstitutionOwner {
        Objects.requireNonNull(holderName, "holderName");
    }

    @Override
    public AccountType accountType() {
        return AccountType.FUNDING;
    }
}
