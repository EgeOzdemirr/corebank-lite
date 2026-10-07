package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import java.util.Objects;
import java.util.UUID;

/** Identity of a transfer. It is also the posting id of the transfer's ledger posting (ADR-0004). */
public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "value");
    }

    public static TransferId newId() {
        return new TransferId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
