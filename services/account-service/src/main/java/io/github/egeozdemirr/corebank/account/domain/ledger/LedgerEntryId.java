package io.github.egeozdemirr.corebank.account.domain.ledger;

import java.util.Objects;
import java.util.UUID;

/** Identity of a single ledger line. */
public record LedgerEntryId(UUID value) {

    public LedgerEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static LedgerEntryId newId() {
        return new LedgerEntryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
