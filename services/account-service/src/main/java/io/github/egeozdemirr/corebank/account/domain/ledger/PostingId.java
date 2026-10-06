package io.github.egeozdemirr.corebank.account.domain.ledger;

import java.util.Objects;
import java.util.UUID;

/** Identity of a posting: one balanced set of ledger entries. */
public record PostingId(UUID value) {

    public PostingId {
        Objects.requireNonNull(value, "value");
    }

    public static PostingId newId() {
        return new PostingId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
