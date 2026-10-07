package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** The transfer passed validation and the single transaction limit and was recorded. Nobody has approved it yet. */
public record TransferRequested(TransferId transferId, TransferOrder order, boolean approvalRequired,
                                Instant requestedAt) implements TransferEvent {

    public TransferRequested {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    @Override
    public Optional<UserId> checker() {
        return Optional.empty();
    }

    @Override
    public Instant occurredAt() {
        return requestedAt;
    }
}
