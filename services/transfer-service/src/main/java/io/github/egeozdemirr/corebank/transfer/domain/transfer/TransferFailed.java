package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** account-service definitely rejected the posting; an unknown outcome never produces this event. */
public record TransferFailed(TransferId transferId, TransferOrder order, Optional<UserId> checker,
                             FailureReason reason, Instant failedAt) implements TransferEvent {

    public TransferFailed {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(checker, "checker");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(failedAt, "failedAt");
    }

    @Override
    public Instant occurredAt() {
        return failedAt;
    }
}
