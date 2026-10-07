package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Approved by a checker (above the threshold) or automatically (below it, {@code checker} is empty). */
public record TransferApproved(TransferId transferId, TransferOrder order, Optional<UserId> checker,
                               Instant approvedAt) implements TransferEvent {

    public TransferApproved {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(checker, "checker");
        Objects.requireNonNull(approvedAt, "approvedAt");
    }

    @Override
    public Instant occurredAt() {
        return approvedAt;
    }
}
