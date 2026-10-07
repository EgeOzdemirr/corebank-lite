package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import java.util.Objects;

/** A customer account as account-service reports it: enough to reference it in a transfer, and whether it is open. */
public record CustomerAccount(AccountReference reference, boolean active) {

    public CustomerAccount {
        Objects.requireNonNull(reference, "reference");
    }
}
