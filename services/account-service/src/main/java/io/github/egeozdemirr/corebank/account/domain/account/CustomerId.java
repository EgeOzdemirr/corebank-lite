package io.github.egeozdemirr.corebank.account.domain.account;

import java.util.Objects;
import java.util.UUID;

/** Identity of the customer who owns an account; issued by customer-service. */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
