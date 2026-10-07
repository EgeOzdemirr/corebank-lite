package io.github.egeozdemirr.corebank.transfer.domain.account;

import java.util.Objects;
import java.util.UUID;

/** Identity of an account in account-service; transfer-service only refers to accounts, it does not own them. */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
